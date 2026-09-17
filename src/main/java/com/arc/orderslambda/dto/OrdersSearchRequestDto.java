package com.arc.orderslambda.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Inbound search request DTO.
 *
 * <p>Bean Validation (JSR-380 / GP-01) constraints are declared here so that
 * {@link com.arc.orderslambda.service.OrdersSearchService} can reject invalid
 * requests before they ever reach the DynamoDB layer.
 */
public class OrdersSearchRequestDto {

    /**
     * Customer identifier — required, alphanumeric with hyphens, max 64 chars.
     */
    @NotBlank(message = "customerId must not be blank")
    @Size(max = 64, message = "customerId must not exceed 64 characters")
    @Pattern(regexp = "^[a-zA-Z0-9\\-]+$", message = "customerId contains invalid characters")
    private String customerId;

    /**
     * Optional order status filter (PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED).
     * Null means "all statuses".
     */
    @Pattern(
        regexp = "^(PENDING|CONFIRMED|SHIPPED|DELIVERED|CANCELLED)?$",
        message = "status must be one of PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED"
    )
    private String status;

    /**
     * Maximum number of results to return. 1–100, defaults to 20 if not supplied.
     */
    @Min(value = 1, message = "limit must be at least 1")
    @Max(value = 100, message = "limit must not exceed 100")
    private int limit = 20;

    // ── Constructors ──────────────────────────────────────────────────────────

    public OrdersSearchRequestDto() {}

    public OrdersSearchRequestDto(String customerId, String status, int limit) {
        this.customerId = customerId;
        this.status = status;
        this.limit = limit;
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }
}
