package com.arc.orderslambda.dto;

/**
 * Individual order item returned in {@link OrdersSearchResponseDto}.
 *
 * <p>All PII fields ({@code maskedCustomerName}, {@code maskedEmail},
 * {@code maskedShippingAddress}) are pre-masked by
 * {@link com.arc.orderslambda.util.PiiMaskingUtil} and must never contain
 * raw personally-identifiable information.
 */
public class OrderDto {

    private String orderId;
    private String customerId;
    private String status;
    private String orderDate;          // ISO-8601, e.g. "2024-03-15"
    private String maskedCustomerName;
    private String maskedEmail;
    private String maskedShippingAddress;

    // ── Constructors ──────────────────────────────────────────────────────────

    public OrderDto() {}

    public OrderDto(
            String orderId,
            String customerId,
            String status,
            String orderDate,
            String maskedCustomerName,
            String maskedEmail,
            String maskedShippingAddress) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.status = status;
        this.orderDate = orderDate;
        this.maskedCustomerName = maskedCustomerName;
        this.maskedEmail = maskedEmail;
        this.maskedShippingAddress = maskedShippingAddress;
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getOrderDate() { return orderDate; }
    public void setOrderDate(String orderDate) { this.orderDate = orderDate; }

    public String getMaskedCustomerName() { return maskedCustomerName; }
    public void setMaskedCustomerName(String maskedCustomerName) {
        this.maskedCustomerName = maskedCustomerName;
    }

    public String getMaskedEmail() { return maskedEmail; }
    public void setMaskedEmail(String maskedEmail) { this.maskedEmail = maskedEmail; }

    public String getMaskedShippingAddress() { return maskedShippingAddress; }
    public void setMaskedShippingAddress(String maskedShippingAddress) {
        this.maskedShippingAddress = maskedShippingAddress;
    }
}
