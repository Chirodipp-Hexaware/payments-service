package com.arc.orderslambda.repository;

import com.arc.orderslambda.model.Order;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.EnhancedGlobalSecondaryIndex;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link OrderRepository} against a real DynamoDB Local instance
 * (Testcontainers). Verifies GSI query behavior, the {@code limit} bound, and
 * primary-key {@code GetItem} hit/miss — contract details a mock cannot catch
 * (REQ-1, REQ-3, REQ-4). Design §9.2.
 *
 * <p>Tagged {@code integration}; requires Docker. Excluded from the fast on-save unit run.
 * Arrange–Act–Assert; deterministic (fixed seed data, no wall-clock/network dependence
 * beyond the local container).
 */
@Tag("integration")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderRepositoryIntegrationTest {

    private static final String TABLE = "orders-it";
    private static final int DYNAMO_PORT = 8000;

    @Container
    private final GenericContainer<?> dynamo =
            new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:2.5.2"))
                    .withExposedPorts(DYNAMO_PORT);

    private DynamoDbClient lowLevel;
    private DynamoDbTable<Order> table;
    private OrderRepository repository;

    @BeforeAll
    void setUp() {
        String endpoint = "http://" + dynamo.getHost() + ":" + dynamo.getMappedPort(DYNAMO_PORT);

        lowLevel = DynamoDbClient.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
                .httpClient(UrlConnectionHttpClient.create())
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("local", "local")))
                .build();

        DynamoDbEnhancedClient enhanced = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(lowLevel)
                .build();

        table = enhanced.table(TABLE, TableSchema.fromBean(Order.class));
        table.createTable(builder -> builder
                .globalSecondaryIndices(EnhancedGlobalSecondaryIndex.builder()
                        .indexName(Order.GSI_CUSTOMER_DATE)
                        .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                        .build()));

        seed();

        repository = new OrderRepository(enhanced, TABLE);
    }

    @AfterAll
    void tearDown() {
        if (lowLevel != null) {
            lowLevel.close();
        }
    }

    private void seed() {
        // cust-1: three orders on ascending dates so newest-first ordering is observable
        table.putItem(order("ord-1", "cust-1", "PENDING",   "2024-01-01"));
        table.putItem(order("ord-2", "cust-1", "SHIPPED",   "2024-02-01"));
        table.putItem(order("ord-3", "cust-1", "DELIVERED", "2024-03-01"));
        // a different customer to prove partition scoping
        table.putItem(order("ord-9", "cust-2", "PENDING",   "2024-05-01"));
    }

    private Order order(String orderId, String customerId, String status, String date) {
        Order o = new Order();
        o.setOrderId(orderId);
        o.setCustomerId(customerId);
        o.setStatus(status);
        o.setOrderDate(date);
        o.setCustomerName("Test Name");
        o.setEmail("test@example.com");
        o.setShippingAddress("1 Test St");
        return o;
    }

    // ── REQ-1: GSI query returns newest-first, partition-scoped ────────────────────

    @Test
    @DisplayName("findByCustomerId returns that customer's orders newest-first")
    void findByCustomerId_returnsNewestFirst() {
        List<Order> result = repository.findByCustomerId("cust-1", 20);

        assertThat(result).extracting(Order::getOrderId)
                .containsExactly("ord-3", "ord-2", "ord-1");
        assertThat(result).extracting(Order::getCustomerId)
                .containsOnly("cust-1"); // no cross-partition bleed
    }

    // ── REQ-3: limit honored ───────────────────────────────────────────────────────

    @Test
    @DisplayName("findByCustomerId honors the limit (returns at most `limit` items)")
    void findByCustomerId_honorsLimit() {
        List<Order> result = repository.findByCustomerId("cust-1", 2);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Order::getOrderId)
                .containsExactly("ord-3", "ord-2"); // newest two
    }

    @Test
    @DisplayName("findByCustomerId returns empty list for an unknown customer")
    void findByCustomerId_unknownCustomer_returnsEmpty() {
        assertThat(repository.findByCustomerId("no-such-customer", 20)).isEmpty();
    }

    // ── REQ-4: GetItem hit / miss ──────────────────────────────────────────────────

    @Test
    @DisplayName("findByOrderId returns the matching order (hit)")
    void findByOrderId_hit() {
        Optional<Order> found = repository.findByOrderId("ord-2");

        assertThat(found).isPresent();
        assertThat(found.get().getCustomerId()).isEqualTo("cust-1");
        assertThat(found.get().getStatus()).isEqualTo("SHIPPED");
    }

    @Test
    @DisplayName("findByOrderId returns empty for an unknown id (miss)")
    void findByOrderId_miss() {
        assertThat(repository.findByOrderId("does-not-exist")).isEmpty();
    }
}
