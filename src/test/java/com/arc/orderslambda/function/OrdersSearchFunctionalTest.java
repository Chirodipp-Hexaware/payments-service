package com.arc.orderslambda.function;

import com.arc.orderslambda.auth.ScopeValidator;
import com.arc.orderslambda.model.Order;
import com.arc.orderslambda.repository.OrderRepository;
import com.arc.orderslambda.service.OrdersSearchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Functional (black-box) tests exercising the full request path per acceptance criteria
 * (design §9.3): real {@link ScopeValidator}, {@link OrdersSearchService} (with real
 * {@code PiiMaskingUtil} and Bean Validation) and both functions wired together — only the
 * external data store ({@link OrderRepository}) is mocked.
 *
 * <p>Auth uses real HS256-signed JWTs so the scope path is genuinely exercised.
 * Arrange–Act–Assert; deterministic.
 */
@ExtendWith(MockitoExtension.class)
class OrdersSearchFunctionalTest {

    private static final byte[] TEST_SECRET = "0123456789abcdef0123456789abcdef".getBytes();

    @Mock private OrderRepository repository;

    private OrdersSearchFunction searchFunction;
    private OrdersSearchByOrderIdFunction lookupFunction;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();
        OrdersSearchService service = new OrdersSearchService(repository, validator);
        ScopeValidator scopeValidator = new ScopeValidator();
        searchFunction = new OrdersSearchFunction(service, scopeValidator, objectMapper);
        lookupFunction = new OrdersSearchByOrderIdFunction(service, scopeValidator, objectMapper);
    }

    // ── Acceptance: search returns masked orders newest-first (REQ-1, REQ-7) ────────

    @Test
    @DisplayName("Authorized search returns masked orders and a count")
    void search_authorized_returnsMaskedOrders() throws Exception {
        when(repository.findByCustomerId(eq("cust-1"), anyInt())).thenReturn(List.of(
                order("ord-2", "cust-1", "SHIPPED", "2024-03-10",
                        "David Lee", "david.lee@corp.com", "22 Elm Street, Boston"),
                order("ord-1", "cust-1", "PENDING", "2024-01-01",
                        "David Lee", "david.lee@corp.com", "22 Elm Street, Boston")));

        Map<String, Object> resp = searchFunction.apply(searchEvent(
                validToken(), Map.of("customerId", "cust-1")));

        assertThat(resp.get("statusCode")).isEqualTo(200);
        JsonNode body = objectMapper.readTree((String) resp.get("body"));
        assertThat(body.get("count").asInt()).isEqualTo(2);

        JsonNode first = body.get("orders").get(0);
        // Real masking applied through the service
        assertThat(first.get("maskedCustomerName").asText()).isEqualTo("D**** L**");
        assertThat(first.get("maskedEmail").asText()).isEqualTo("*****.lee@corp.com");
        assertThat(first.get("maskedShippingAddress").asText()).isEqualTo("22 *** ******, ******");
        // Raw PII must not appear anywhere in the response body
        String raw = (String) resp.get("body");
        assertThat(raw).doesNotContain("David Lee");
        assertThat(raw).doesNotContain("david.lee@corp.com");
        assertThat(raw).doesNotContain("Elm Street");
    }

    // ── Acceptance: status filter (REQ-2) ───────────────────────────────────────────

    @Test
    @DisplayName("Status filter returns only matching orders")
    void search_statusFilter_returnsOnlyMatching() throws Exception {
        when(repository.findByCustomerId(eq("cust-1"), anyInt())).thenReturn(List.of(
                order("ord-2", "cust-1", "SHIPPED", "2024-03-10", "A B", "a@b.com", "1 X"),
                order("ord-1", "cust-1", "PENDING", "2024-01-01", "A B", "a@b.com", "1 X")));

        Map<String, Object> resp = searchFunction.apply(searchEvent(
                validToken(), Map.of("customerId", "cust-1", "status", "SHIPPED")));

        assertThat(resp.get("statusCode")).isEqualTo(200);
        JsonNode body = objectMapper.readTree((String) resp.get("body"));
        assertThat(body.get("count").asInt()).isEqualTo(1);
        assertThat(body.get("orders").get(0).get("status").asText()).isEqualTo("SHIPPED");
    }

    // ── Acceptance: missing customerId → 400 (REQ-1) ───────────────────────────────

    @Test
    @DisplayName("Search without customerId returns 400")
    void search_missingCustomerId_returns400() {
        Map<String, Object> resp = searchFunction.apply(searchEvent(validToken(), Map.of()));

        assertThat(resp.get("statusCode")).isEqualTo(400);
        assertThat((String) resp.get("body")).contains("customerId");
    }

    // ── Acceptance: unauthorized → 401 (REQ-6) ─────────────────────────────────────

    @Test
    @DisplayName("Search with a token lacking orders:read returns 401")
    void search_insufficientScope_returns401() throws Exception {
        String token = signedToken(new JWTClaimsSet.Builder()
                .claim("scope", "orders:write").build());

        Map<String, Object> resp = searchFunction.apply(searchEvent(
                token, Map.of("customerId", "cust-1")));

        assertThat(resp.get("statusCode")).isEqualTo(401);
    }

    @Test
    @DisplayName("Search with no Authorization header returns 401")
    void search_noToken_returns401() {
        Map<String, Object> resp = searchFunction.apply(
                Map.of("queryStringParameters", Map.of("customerId", "cust-1")));

        assertThat(resp.get("statusCode")).isEqualTo(401);
    }

    // ── Acceptance: lookup miss → 200 count 0 (REQ-4) ──────────────────────────────

    @Test
    @DisplayName("Lookup for unknown orderId returns 200 with count 0")
    void lookup_unknownId_returns200Count0() throws Exception {
        when(repository.findByOrderId(eq("nope"))).thenReturn(Optional.empty());

        Map<String, Object> resp = lookupFunction.apply(searchEvent(
                validToken(), Map.of("orderId", "nope")));

        assertThat(resp.get("statusCode")).isEqualTo(200);
        JsonNode body = objectMapper.readTree((String) resp.get("body"));
        assertThat(body.get("count").asInt()).isEqualTo(0);
        assertThat(body.get("orders")).isEmpty();
    }

    @Test
    @DisplayName("Lookup for known orderId returns 200 with one masked order")
    void lookup_knownId_returns200Count1() throws Exception {
        when(repository.findByOrderId(eq("ord-7"))).thenReturn(Optional.of(
                order("ord-7", "cust-1", "DELIVERED", "2024-02-01",
                        "Jane Roe", "jane.roe@mail.com", "5 Oak Ave")));

        Map<String, Object> resp = lookupFunction.apply(searchEvent(
                validToken(), Map.of("orderId", "ord-7")));

        assertThat(resp.get("statusCode")).isEqualTo(200);
        JsonNode body = objectMapper.readTree((String) resp.get("body"));
        assertThat(body.get("count").asInt()).isEqualTo(1);
        assertThat(body.get("orders").get(0).get("maskedEmail").asText())
                .isEqualTo("*****.roe@mail.com");
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────

    private Map<String, Object> searchEvent(String token, Map<String, String> query) {
        return Map.of(
                "headers", Map.of("authorization", "Bearer " + token),
                "queryStringParameters", query);
    }

    private String validToken() {
        try {
            return signedToken(new JWTClaimsSet.Builder()
                    .claim("scope", "orders:read").build());
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String signedToken(JWTClaimsSet claims) throws JOSEException {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(TEST_SECRET));
        return jwt.serialize();
    }

    private Order order(String orderId, String customerId, String status,
                        String orderDate, String name, String email, String address) {
        Order o = new Order();
        o.setOrderId(orderId);
        o.setCustomerId(customerId);
        o.setStatus(status);
        o.setOrderDate(orderDate);
        o.setCustomerName(name);
        o.setEmail(email);
        o.setShippingAddress(address);
        return o;
    }
}
