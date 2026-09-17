package com.arc.orderslambda.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * DynamoDB table-mapped entity for the Orders table.
 *
 * <p>Table key design:
 * <ul>
 *   <li>PK: {@code orderId} (partition key)</li>
 *   <li>GSI "customerId-orderDate-index":
 *       partition key {@code customerId}, sort key {@code orderDate} — used for search queries.</li>
 * </ul>
 *
 * <p>The table name is resolved at runtime from the {@code ORDERS_TABLE_NAME}
 * environment variable (GP-02 — no hardcoded names).
 */
@DynamoDbBean
public class Order {

    /** GSI name used for customer-scoped date-range queries. */
    public static final String GSI_CUSTOMER_DATE = "customerId-orderDate-index";

    private String orderId;
    private String customerId;
    private String status;
    private String orderDate;        // ISO-8601 date string for lexicographic sort
    private String customerName;     // raw PII — masked before leaving the service tier
    private String email;            // raw PII
    private String shippingAddress;  // raw PII

    // ── DynamoDB key mappings ─────────────────────────────────────────────────

    @DynamoDbPartitionKey
    @DynamoDbAttribute("orderId")
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    @DynamoDbSecondaryPartitionKey(indexNames = GSI_CUSTOMER_DATE)
    @DynamoDbAttribute("customerId")
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    @DynamoDbAttribute("status")
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @DynamoDbSecondarySortKey(indexNames = GSI_CUSTOMER_DATE)
    @DynamoDbAttribute("orderDate")
    public String getOrderDate() { return orderDate; }
    public void setOrderDate(String orderDate) { this.orderDate = orderDate; }

    @DynamoDbAttribute("customerName")
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    @DynamoDbAttribute("email")
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    @DynamoDbAttribute("shippingAddress")
    public String getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
    }
}
