package com.arc.orderslambda.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Inbound request DTO for the {@code GET /orders/searchByOrderId} endpoint.
 *
 * <p>Bean Validation (JSR-380 / GP-01) constraints are declared here so that
 * {@link com.arc.orderslambda.service.OrdersSearchService} can reject invalid
 * requests before they ever reach the DynamoDB layer.
 *
 * <p>Satisfies REQ-4 (input validation — GP-01).
 */
public class OrdersSearchByOrderIdRequestDto {

    /**
     * Order identifier — required, alphanumeric with hyphens, max 64 chars.
     * Matches the {@code orderId} partition key of the DynamoDB orders table.
     */
    @NotBlank(message = "orderId must not be blank")
    @Size(max = 64, message = "orderId must not exceed 64 characters")
    @Pattern(regexp = "^[a-zA-Z0-9\\-]+$", message = "orderId contains invalid characters")
    private String orderId;

    // ── Constructors ──────────────────────────────────────────────────────────

    public OrdersSearchByOrderIdRequestDto() {}

    public OrdersSearchByOrderIdRequestDto(String orderId) {
        this.orderId = orderId;
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }
}
