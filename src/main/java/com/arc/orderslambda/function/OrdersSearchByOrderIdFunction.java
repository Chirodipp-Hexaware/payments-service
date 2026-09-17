package com.arc.orderslambda.function;

import com.arc.orderslambda.auth.ScopeValidator;
import com.arc.orderslambda.dto.OrdersSearchByOrderIdRequestDto;
import com.arc.orderslambda.dto.OrdersSearchResponseDto;
import com.arc.orderslambda.service.OrdersSearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Spring Cloud Function handler for the {@code GET /orders/searchByOrderId} endpoint.
 *
 * <p>The function receives an API Gateway v2 HTTP event represented as a
 * {@code Map<String, Object>} and:
 * <ol>
 *   <li>Validates the {@code Authorization} header (defence-in-depth scope check — REQ-4 /
 *       GP-06).</li>
 *   <li>Reads the required {@code orderId} query-string parameter (GP-01).</li>
 *   <li>Delegates to {@link OrdersSearchService#searchByOrderId} for Bean Validation,
 *       DynamoDB {@code GetItem}, and PII masking (REQ-4 / GP-03).</li>
 *   <li>Returns an API Gateway v2 proxy response with the JSON body and status code.</li>
 * </ol>
 *
 * <p>The bean name {@code "ordersSearchByOrderIdFunction"} must match the value added to
 * {@code spring.cloud.function.definition} in {@code application.yml}.
 */
@Component("ordersSearchByOrderIdFunction")
public class OrdersSearchByOrderIdFunction implements Function<Map<String, Object>, Map<String, Object>> {

    private static final Logger log = LoggerFactory.getLogger(OrdersSearchByOrderIdFunction.class);

    private static final int HTTP_OK            = 200;
    private static final int HTTP_BAD_REQUEST   = 400;
    private static final int HTTP_UNAUTHORIZED  = 401;
    private static final int HTTP_INTERNAL_ERROR = 500;

    private final OrdersSearchService ordersSearchService;
    private final ScopeValidator scopeValidator;
    private final ObjectMapper objectMapper;

    public OrdersSearchByOrderIdFunction(
            OrdersSearchService ordersSearchService,
            ScopeValidator scopeValidator,
            ObjectMapper objectMapper) {
        this.ordersSearchService = ordersSearchService;
        this.scopeValidator = scopeValidator;
        this.objectMapper = objectMapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(Map<String, Object> event) {
        log.debug("Received Lambda event for searchByOrderId");

        // ── 1. Scope / Auth validation ────────────────────────────────────────
        try {
            Map<String, String> headers = extractHeaders(event);
            String authHeader = headers.getOrDefault("authorization",
                    headers.get("Authorization"));
            scopeValidator.validate(authHeader);
        } catch (SecurityException ex) {
            log.warn("Authorization failure: {}", ex.getMessage());
            return errorResponse(HTTP_UNAUTHORIZED, ex.getMessage());
        }

        // ── 2. Parse query-string parameters ─────────────────────────────────
        OrdersSearchByOrderIdRequestDto requestDto;
        try {
            requestDto = buildRequestDto(event);
        } catch (IllegalArgumentException ex) {
            log.warn("Bad request: {}", ex.getMessage());
            return errorResponse(HTTP_BAD_REQUEST, ex.getMessage());
        }

        // ── 3. Execute lookup ─────────────────────────────────────────────────
        try {
            OrdersSearchResponseDto response = ordersSearchService.searchByOrderId(requestDto);
            String body = objectMapper.writeValueAsString(response);
            return successResponse(body);
        } catch (IllegalArgumentException ex) {
            log.warn("Validation error: {}", ex.getMessage());
            return errorResponse(HTTP_BAD_REQUEST, ex.getMessage());
        } catch (Exception ex) {
            log.error("Unhandled exception during searchByOrderId", ex);
            return errorResponse(HTTP_INTERNAL_ERROR, "Internal server error");
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, String> extractHeaders(Map<String, Object> event) {
        Object raw = event.get("headers");
        if (raw instanceof Map<?, ?> rawMap) {
            Map<String, String> headers = new HashMap<>();
            rawMap.forEach((k, v) -> headers.put(String.valueOf(k), String.valueOf(v)));
            return headers;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private OrdersSearchByOrderIdRequestDto buildRequestDto(Map<String, Object> event) {
        Map<String, String> queryParams = Map.of();
        Object raw = event.get("queryStringParameters");
        if (raw instanceof Map<?, ?> rawMap) {
            Map<String, String> params = new HashMap<>();
            rawMap.forEach((k, v) -> params.put(String.valueOf(k), String.valueOf(v)));
            queryParams = params;
        }

        String orderId = queryParams.get("orderId");
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("Query parameter 'orderId' is required");
        }

        return new OrdersSearchByOrderIdRequestDto(orderId);
    }

    private Map<String, Object> successResponse(String jsonBody) {
        Map<String, Object> response = new HashMap<>();
        response.put("statusCode", HTTP_OK);
        response.put("headers", Map.of("Content-Type", "application/json"));
        response.put("body", jsonBody);
        return response;
    }

    private Map<String, Object> errorResponse(int statusCode, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("statusCode", statusCode);
        response.put("headers", Map.of("Content-Type", "application/json"));
        response.put("body", "{\"error\":\"" + escapeJson(message) + "\"}");
        return response;
    }

    /** Minimal JSON string escaping to avoid injection in error body. */
    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r");
    }
}
