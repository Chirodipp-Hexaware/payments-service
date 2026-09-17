package com.arc.orderslambda.function;

import com.arc.orderslambda.auth.ScopeValidator;
import com.arc.orderslambda.dto.OrderDto;
import com.arc.orderslambda.dto.OrdersSearchByOrderIdRequestDto;
import com.arc.orderslambda.dto.OrdersSearchResponseDto;
import com.arc.orderslambda.service.OrdersSearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OrdersSearchByOrderIdFunction} — the Lambda boundary for
 * {@code GET /orders/searchByOrderId}. Covers valid lookup, empty result, missing
 * {@code orderId}, auth failure, and error mapping (REQ-4, REQ-6, REQ-8).
 *
 * <p>Collaborators mocked; real {@link ObjectMapper}. Arrange–Act–Assert; deterministic.
 */
@ExtendWith(MockitoExtension.class)
class OrdersSearchByOrderIdFunctionTest {

    @Mock private OrdersSearchService service;
    @Mock private ScopeValidator scopeValidator;

    private OrdersSearchByOrderIdFunction function;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        function = new OrdersSearchByOrderIdFunction(service, scopeValidator, objectMapper);
    }

    private Map<String, Object> event(Map<String, String> headers, Map<String, String> query) {
        return Map.of("headers", headers, "queryStringParameters", query);
    }

    private Map<String, String> authHeader() {
        return Map.of("authorization", "Bearer token");
    }

    // ── 200 found ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Valid lookup returns 200 with a single masked order, count 1")
    void apply_validLookup_returns200Count1() throws Exception {
        OrderDto dto = new OrderDto("ord-7", "cust-1", "DELIVERED", "2024-02-01",
                "B**", "***b@x.io", "9 ***");
        when(service.searchByOrderId(any()))
                .thenReturn(new OrdersSearchResponseDto(List.of(dto)));

        Map<String, Object> resp = function.apply(
                event(authHeader(), Map.of("orderId", "ord-7")));

        assertThat(resp.get("statusCode")).isEqualTo(200);
        var body = objectMapper.readTree((String) resp.get("body"));
        assertThat(body.get("count").asInt()).isEqualTo(1);
        assertThat(body.get("orders").get(0).get("orderId").asText()).isEqualTo("ord-7");

        ArgumentCaptor<OrdersSearchByOrderIdRequestDto> captor =
                ArgumentCaptor.forClass(OrdersSearchByOrderIdRequestDto.class);
        verify(service).searchByOrderId(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo("ord-7");
    }

    // ── 200 not found (not an error) ──────────────────────────────────────────────

    @Test
    @DisplayName("Lookup miss returns 200 with empty list, count 0")
    void apply_lookupMiss_returns200Count0() throws Exception {
        when(service.searchByOrderId(any()))
                .thenReturn(new OrdersSearchResponseDto(List.of()));

        Map<String, Object> resp = function.apply(
                event(authHeader(), Map.of("orderId", "does-not-exist")));

        assertThat(resp.get("statusCode")).isEqualTo(200);
        var body = objectMapper.readTree((String) resp.get("body"));
        assertThat(body.get("count").asInt()).isEqualTo(0);
        assertThat(body.get("orders")).isEmpty();
    }

    // ── 400 branches ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Missing orderId returns 400 and never calls the service")
    void apply_missingOrderId_returns400() {
        Map<String, Object> resp = function.apply(event(authHeader(), Map.of()));

        assertThat(resp.get("statusCode")).isEqualTo(400);
        assertThat((String) resp.get("body")).contains("orderId");
        verify(service, never()).searchByOrderId(any());
    }

    @Test
    @DisplayName("Service validation failure maps to 400")
    void apply_serviceValidationError_returns400() {
        when(service.searchByOrderId(any()))
                .thenThrow(new IllegalArgumentException("Validation failed: orderId"));

        Map<String, Object> resp = function.apply(
                event(authHeader(), Map.of("orderId", "ord-1")));

        assertThat(resp.get("statusCode")).isEqualTo(400);
        assertThat((String) resp.get("body")).contains("Validation failed");
    }

    // ── 401 branch ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Auth failure returns 401 and never calls the service")
    void apply_authFailure_returns401() {
        doThrow(new SecurityException("Insufficient scope"))
                .when(scopeValidator).validate(any());

        Map<String, Object> resp = function.apply(
                event(Map.of("authorization", "Bearer bad"), Map.of("orderId", "ord-1")));

        assertThat(resp.get("statusCode")).isEqualTo(401);
        verify(service, never()).searchByOrderId(any());
    }

    // ── 500 branch ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Unhandled exception maps to generic 500 without leaking detail")
    void apply_unhandledException_returns500Generic() {
        when(service.searchByOrderId(any()))
                .thenThrow(new RuntimeException("GetItem failed: table=orders"));

        Map<String, Object> resp = function.apply(
                event(authHeader(), Map.of("orderId", "ord-1")));

        assertThat(resp.get("statusCode")).isEqualTo(500);
        assertThat((String) resp.get("body")).isEqualTo("{\"error\":\"Internal server error\"}");
        assertThat((String) resp.get("body")).doesNotContain("GetItem");
    }
}
