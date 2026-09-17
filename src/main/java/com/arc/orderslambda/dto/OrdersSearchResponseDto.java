package com.arc.orderslambda.dto;

import java.util.Collections;
import java.util.List;

/**
 * Outbound search response DTO.
 *
 * <p>All {@link OrderDto} items in this response have had PII masked by
 * {@link com.arc.orderslambda.util.PiiMaskingUtil} before being included here.
 */
public class OrdersSearchResponseDto {

    private List<OrderDto> orders;
    private int count;

    // ── Constructors ──────────────────────────────────────────────────────────

    public OrdersSearchResponseDto() {
        this.orders = Collections.emptyList();
        this.count = 0;
    }

    public OrdersSearchResponseDto(List<OrderDto> orders) {
        this.orders = orders != null ? List.copyOf(orders) : Collections.emptyList();
        this.count = this.orders.size();
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public List<OrderDto> getOrders() {
        return orders;
    }

    public void setOrders(List<OrderDto> orders) {
        this.orders = orders != null ? List.copyOf(orders) : Collections.emptyList();
        this.count = this.orders.size();
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }
}
