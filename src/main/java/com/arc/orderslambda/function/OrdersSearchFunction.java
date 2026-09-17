package com.arc.orderslambda.function;

import com.arc.orderslambda.auth.ScopeValidator;
import com.arc.orderslambda.dto.OrdersSearchRequestDto;
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
 * Spring Cloud Function handler for the Orders Search Lambda.
 *
 * <p>The function receives an API Gateway v2 HTTP event represented as a
 * {@code Map<String, Object>} (the Spring Cloud Function AWS adapter deserialises the
 * raw Lambda JSON payload into this type). It:
 * <ol>
 *   <li>Extracts and validates the {@code Authorization} header (scope enforcement).</li>
 *   <li>Reads query-string parameters ({@code customerId}, {@code status}, {@code limit}).</li>
 *   <li>Delegates to {@link OrdersSearchService} for validation, DynamoDB query, and PII masking.</li>
 *   <li>Returns an API Gateway v2 proxy response map with the JSON body and appropriate status code.</li>
 * </ol>
 *
 * <p>The bean name {@code "ordersSearchFunction"} matches the value of
 * {@code spring.cloud.function.definition} in {@code application.yml}.
 */
@Component("ordersSearchFunction")
public class OrdersSearchFunction implements Function<Map<String, Object>, Map<String, Object>> {

    private static final Logger log = LoggerFactory.getLogger(OrdersSearchFunction.class);

    private static final int HTTP_OK = 200;
    private static final int HTTP_BAD_REQUEST = 400;
    private static final int HTTP_UNAUTHORIZED = 401;
    private static final int HTTP_INTERNAL_ERROR = 500;

    private final OrdersSearchService ordersSearchService;
    private final ScopeValidator scopeValidator;
    private final ObjectMapper objectMapper;

    public OrdersSearchFunction(
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
        log.debug("Received Lambda event");

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
        OrdersSearchRequestDto requestDto;
        try {
            requestDto = buildRequestDto(event);
        } catch (IllegalArgumentException ex) {
            log.warn("Bad request: {}", ex.getMessage());
            return errorResponse(HTTP_BAD_REQUEST, ex.getMessage());
        }

        // ── 3. Execute search ─────────────────────────────────────────────────
        try {
            OrdersSearchResponseDto response = ordersSearchService.search(requestDto);
            String body = objectMapper.writeValueAsString(response);
            return successResponse(body);
        } catch (IllegalArgumentException ex) {
            log.warn("Validation error: {}", ex.getMessage());
            return errorResponse(HTTP_BAD_REQUEST, ex.getMessage());
        } catch (Exception ex) {
            log.error("Unhandled exception during orders search", ex);
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
    private OrdersSearchRequestDto buildRequestDto(Map<String, Object> event) {
        Map<String, String> queryParams = Map.of();
        Object raw = event.get("queryStringParameters");
        if (raw instanceof Map<?, ?> rawMap) {
            Map<String, String> params = new HashMap<>();
            rawMap.forEach((k, v) -> params.put(String.valueOf(k), String.valueOf(v)));
            queryParams = params;
        }

        String customerId = queryParams.get("customerId");
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("Query parameter 'customerId' is required");
        }

        String status = queryParams.getOrDefault("status", null);

        int limit = 20;
        String limitParam = queryParams.get("limit");
        if (limitParam != null && !limitParam.isBlank()) {
            try {
                limit = Integer.parseInt(limitParam);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Query parameter 'limit' must be an integer");
            }
        }

        return new OrdersSearchRequestDto(customerId, status, limit);
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
