package com.arc.orderslambda.function;

import com.arc.orderslambda.auth.ScopeValidator;
import com.arc.orderslambda.dto.OrderDto;
import com.arc.orderslambda.dto.OrdersSearchRequestDto;
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
 * Unit tests for {@link OrdersSearchFunction} — the Lambda boundary for
 * {@code GET /orders/search}. Covers header extraction, query-param parsing,
 * status-code mapping (200/400/401/500) and JSON error escaping
 * (REQ-1, REQ-3, REQ-6, REQ-8).
 *
 * <p>Collaborators are mocked (Mockito); a real {@link ObjectMapper} is used so the
 * serialized body is asserted end-to-end. Arrange–Act–Assert; deterministic.
 */
@ExtendWith(MockitoExtension.class)
class OrdersSearchFunctionTest {

    @Mock private OrdersSearchService service;
    @Mock private ScopeValidator scopeValidator;

    private OrdersSearchFunction function;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        function = new OrdersSearchFunction(service, scopeValidator, objectMapper);
    }

    private Map<String, Object> event(Map<String, String> headers, Map<String, String> query) {
        return Map.of("headers", headers, "queryStringParameters", query);
    }

    private Map<String, String> authHeader() {
        return Map.of("authorization", "Bearer token");
    }

    // ── 200 happy path ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Valid search returns 200 with JSON body and count")
    void apply_validSearch_returns200() throws Exception {
        OrderDto dto = new OrderDto("ord-1", "cust-1", "SHIPPED", "2024-03-10",
                "A****", "****e@x.com", "1 ***");
        when(service.search(any())).thenReturn(new OrdersSearchResponseDto(List.of(dto)));

        Map<String, Object> resp = function.apply(
                event(authHeader(), Map.of("customerId", "cust-1")));

        assertThat(resp.get("statusCode")).isEqualTo(200);
        assertThat(((Map<?, ?>) resp.get("headers")).get("Content-Type")).isEqualTo("application/json");
        var body = objectMapper.readTree((String) resp.get("body"));
        assertThat(body.get("count").asInt()).isEqualTo(1);
        assertThat(body.get("orders").get(0).get("orderId").asText()).isEqualTo("ord-1");
    }

    @Test
    @DisplayName("customerId, status and limit are parsed and passed to the service")
    void apply_parsesQueryParams() throws Exception {
        when(service.search(any())).thenReturn(new OrdersSearchResponseDto(List.of()));

        function.apply(event(authHeader(),
                Map.of("customerId", "cust-9", "status", "SHIPPED", "limit", "5")));

        ArgumentCaptor<OrdersSearchRequestDto> captor =
                ArgumentCaptor.forClass(OrdersSearchRequestDto.class);
        verify(service).search(captor.capture());
        assertThat(captor.getValue().getCustomerId()).isEqualTo("cust-9");
        assertThat(captor.getValue().getStatus()).isEqualTo("SHIPPED");
        assertThat(captor.getValue().getLimit()).isEqualTo(5);
    }

    @Test
    @DisplayName("Absent limit defaults to 20")
    void apply_absentLimit_defaults20() {
        when(service.search(any())).thenReturn(new OrdersSearchResponseDto(List.of()));

        function.apply(event(authHeader(), Map.of("customerId", "cust-1")));

        ArgumentCaptor<OrdersSearchRequestDto> captor =
                ArgumentCaptor.forClass(OrdersSearchRequestDto.class);
        verify(service).search(captor.capture());
        assertThat(captor.getValue().getLimit()).isEqualTo(20);
    }

    // ── 400 branches ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Missing customerId returns 400 and never calls the service")
    void apply_missingCustomerId_returns400() {
        Map<String, Object> resp = function.apply(event(authHeader(), Map.of()));

        assertThat(resp.get("statusCode")).isEqualTo(400);
        assertThat((String) resp.get("body")).contains("customerId");
        verify(service, never()).search(any());
    }

    @Test
    @DisplayName("Non-integer limit returns 400 'must be an integer'")
    void apply_nonIntegerLimit_returns400() {
        Map<String, Object> resp = function.apply(event(authHeader(),
                Map.of("customerId", "cust-1", "limit", "abc")));

        assertThat(resp.get("statusCode")).isEqualTo(400);
        assertThat((String) resp.get("body")).contains("must be an integer");
    }

    @Test
    @DisplayName("Service validation failure (IllegalArgumentException) maps to 400")
    void apply_serviceValidationError_returns400() {
        when(service.search(any()))
                .thenThrow(new IllegalArgumentException("Validation failed: status"));

        Map<String, Object> resp = function.apply(event(authHeader(),
                Map.of("customerId", "cust-1")));

        assertThat(resp.get("statusCode")).isEqualTo(400);
        assertThat((String) resp.get("body")).contains("Validation failed");
    }

    // ── 401 branch ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Auth failure returns 401 and never calls the service")
    void apply_authFailure_returns401() {
        doThrow(new SecurityException("Insufficient scope"))
                .when(scopeValidator).validate(any());

        Map<String, Object> resp = function.apply(event(
                Map.of("authorization", "Bearer bad"), Map.of("customerId", "cust-1")));

        assertThat(resp.get("statusCode")).isEqualTo(401);
        verify(service, never()).search(any());
    }

    // ── 500 branch + escaping ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Unhandled exception maps to generic 500 without leaking detail")
    void apply_unhandledException_returns500Generic() {
        when(service.search(any())).thenThrow(new RuntimeException("DynamoDB down: table=orders"));

        Map<String, Object> resp = function.apply(event(authHeader(),
                Map.of("customerId", "cust-1")));

        assertThat(resp.get("statusCode")).isEqualTo(500);
        assertThat((String) resp.get("body")).isEqualTo("{\"error\":\"Internal server error\"}");
        assertThat((String) resp.get("body")).doesNotContain("DynamoDB");
    }

    @Test
    @DisplayName("Error message with quotes is JSON-escaped in the body")
    void apply_errorMessageWithQuotes_isEscaped() {
        when(service.search(any()))
                .thenThrow(new IllegalArgumentException("bad \"value\""));

        Map<String, Object> resp = function.apply(event(authHeader(),
                Map.of("customerId", "cust-1")));

        assertThat((String) resp.get("body")).isEqualTo("{\"error\":\"bad \\\"value\\\"\"}");
    }

    @Test
    @DisplayName("Missing headers map yields 401 (no auth header present)")
    void apply_noHeaders_returns401() {
        doThrow(new SecurityException("Missing Authorization header"))
                .when(scopeValidator).validate(any());

        Map<String, Object> resp = function.apply(
                Map.of("queryStringParameters", Map.of("customerId", "cust-1")));

        assertThat(resp.get("statusCode")).isEqualTo(401);
    }
}
