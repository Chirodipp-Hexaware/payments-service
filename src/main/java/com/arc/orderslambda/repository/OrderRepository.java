package com.arc.orderslambda.repository;

import com.arc.orderslambda.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * DynamoDB-backed repository for {@link Order} entities.
 *
 * <p>Queries target the GSI {@value Order#GSI_CUSTOMER_DATE} to retrieve orders
 * by {@code customerId}, sorted by {@code orderDate} in descending order (most-recent first).
 *
 * <p>The table name is resolved from the {@code ORDERS_TABLE_NAME} environment variable
 * at startup — GP-02 (no hardcoded resource names or secrets).
 */
@Repository
public class OrderRepository {

    private static final Logger log = LoggerFactory.getLogger(OrderRepository.class);

    private final DynamoDbTable<Order> table;
    private final DynamoDbIndex<Order> customerDateIndex;

    /**
     * Constructs the repository and wires the DynamoDB table and GSI.
     *
     * @param client          enhanced DynamoDB client (injected by Spring)
     * @param ordersTableName the orders table name resolved from env var {@code ORDERS_TABLE_NAME}
     */
    public OrderRepository(
            DynamoDbEnhancedClient client,
            @Value("${orders.table-name}") String ordersTableName) {

        log.info("Initialising OrderRepository against table '{}'", ordersTableName);
        this.table = client.table(ordersTableName, TableSchema.fromBean(Order.class));
        this.customerDateIndex = this.table.index(Order.GSI_CUSTOMER_DATE);
    }

    /**
     * Queries the GSI for orders belonging to the given {@code customerId}.
     *
     * <p>Results are returned newest-first (DynamoDB sort key is {@code orderDate} in
     * ISO-8601 format, so descending lexicographic order equals descending date order).
     *
     * @param customerId the customer whose orders to retrieve
     * @param limit      maximum number of items to return (1–100)
     * @return list of matched {@link Order} entities (may be empty, never {@code null})
     */
    public List<Order> findByCustomerId(String customerId, int limit) {
        log.debug("Querying GSI '{}' for customerId='{}', limit={}", Order.GSI_CUSTOMER_DATE, customerId, limit);

        QueryConditional keyCondition = QueryConditional
                .keyEqualTo(Key.builder().partitionValue(customerId).build());

        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(keyCondition)
                .limit(limit)
                .scanIndexForward(false) // descending by orderDate
                .build();

        List<Order> results = customerDateIndex.query(request)
                .stream()
                .flatMap(page -> page.items().stream())
                .limit(limit) // safety guard if DynamoDB pages exceed limit
                .collect(Collectors.toList());

        log.debug("GSI query returned {} item(s) for customerId='{}'", results.size(), customerId);
        return results;
    }

    /**
     * Fetches a single order by its primary partition key {@code orderId}.
     *
     * <p>Uses a DynamoDB {@code GetItem} — a direct primary-key lookup that bypasses
     * any GSI and completes in a single round-trip, satisfying the ≤ 10 ms p95 latency
     * budget required by REQ-4.
     *
     * @param orderId the order's partition key value
     * @return an {@link Optional} containing the matching {@link Order}, or empty if
     *         no item exists with that key
     */
    public Optional<Order> findByOrderId(String orderId) {
        log.debug("GetItem on table '{}' for orderId='{}'", table.tableName(), orderId);

        Key key = Key.builder().partitionValue(orderId).build();
        Order result = table.getItem(key);

        log.debug("GetItem returned {} for orderId='{}'", result == null ? "null" : "Order", orderId);
        return Optional.ofNullable(result);
    }
}
