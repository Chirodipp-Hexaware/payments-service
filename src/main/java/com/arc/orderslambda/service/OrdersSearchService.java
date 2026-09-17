package com.arc.orderslambda.service;

import com.arc.orderslambda.dto.OrderDto;
import com.arc.orderslambda.dto.OrdersSearchByOrderIdRequestDto;
import com.arc.orderslambda.dto.OrdersSearchRequestDto;
import com.arc.orderslambda.dto.OrdersSearchResponseDto;
import com.arc.orderslambda.model.Order;
import com.arc.orderslambda.repository.OrderRepository;
import com.arc.orderslambda.util.PiiMaskingUtil;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Business logic for the Orders Search feature.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Validate the incoming {@link OrdersSearchRequestDto} (GP-01).</li>
 *   <li>Delegate to {@link OrderRepository} to query DynamoDB via the GSI.</li>
 *   <li>Apply optional status filtering (DynamoDB does not support filter-on-GSI-partition
 *       natively without a FilterExpression; filtering is done post-query for simplicity
 *       at this scale, consistent with the {@code limit} bound).</li>
 *   <li>Map {@link Order} entities to {@link OrderDto}, applying PII masking via
 *       {@link PiiMaskingUtil}.</li>
 *   <li>Validate the incoming {@link OrdersSearchByOrderIdRequestDto} and perform a
 *       direct primary-key lookup via {@link OrderRepository#findByOrderId} (REQ-4).</li>
 * </ol>
 */
@Service
public class OrdersSearchService {

    private static final Logger log = LoggerFactory.getLogger(OrdersSearchService.class);

    private final OrderRepository orderRepository;
    private final Validator validator;

    public OrdersSearchService(OrderRepository orderRepository, Validator validator) {
        this.orderRepository = orderRepository;
        this.validator = validator;
    }

    /**
     * Searches for orders matching the given criteria.
     *
     * @param request the search request (validated before processing)
     * @return response DTO containing masked order items and a count
     * @throws IllegalArgumentException if the request fails Bean Validation
     */
    public OrdersSearchResponseDto search(OrdersSearchRequestDto request) {
        validateRequest(request);

        log.info("Searching orders for customerId='{}', status='{}', limit={}",
                request.getCustomerId(), request.getStatus(), request.getLimit());

        // Fetch from DynamoDB; over-fetch slightly if status filter is active so we can
        // return up to `limit` items after filtering (capped at 100 to respect IAM/cost bounds).
        int fetchLimit = request.getStatus() != null && !request.getStatus().isBlank()
                ? Math.min(request.getLimit() * 3, 100)
                : request.getLimit();

        List<Order> rawOrders = orderRepository.findByCustomerId(request.getCustomerId(), fetchLimit);

        List<OrderDto> filtered = rawOrders.stream()
                .filter(o -> statusMatches(o.getStatus(), request.getStatus()))
                .limit(request.getLimit())
                .map(this::toDto)
                .collect(Collectors.toList());

        log.info("Returning {} order(s) for customerId='{}'", filtered.size(), request.getCustomerId());
        return new OrdersSearchResponseDto(filtered);
    }

    // ── REQ-4 ─────────────────────────────────────────────────────────────────

    /**
     * Looks up a single order by its primary key ({@code orderId}).
     *
     * <p>Delegates to {@link OrderRepository#findByOrderId}, which issues a DynamoDB
     * {@code GetItem} — a direct partition-key lookup with no GSI fan-out — satisfying
     * the ≤ 10 ms p95 latency budget for result sets under 2 records (REQ-4).
     *
     * <p>PII fields are masked before the {@link OrderDto} is returned (GP-03).
     *
     * @param request the lookup request (validated before processing)
     * @return response DTO containing at most one masked order item and a count of 0 or 1
     * @throws IllegalArgumentException if the request fails Bean Validation
     */
    public OrdersSearchResponseDto searchByOrderId(OrdersSearchByOrderIdRequestDto request) {
        validateByOrderIdRequest(request);

        log.info("Looking up order for orderId='{}'", request.getOrderId());

        Optional<Order> found = orderRepository.findByOrderId(request.getOrderId());

        List<OrderDto> orders = found.map(this::toDto).map(List::of).orElse(List.of());

        log.info("Returning {} order(s) for orderId='{}'", orders.size(), request.getOrderId());
        return new OrdersSearchResponseDto(orders);
    }

    // ── Private helpers ───────────────────────────────────────────────────────
    private void validateRequest(OrdersSearchRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Request must not be null");
        }
        Set<ConstraintViolation<OrdersSearchRequestDto>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String messages = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .collect(Collectors.joining("; "));
            log.warn("Request validation failed: {}", messages);
            throw new IllegalArgumentException("Validation failed: " + messages);
        }
    }

    private void validateByOrderIdRequest(OrdersSearchByOrderIdRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Request must not be null");
        }
        Set<ConstraintViolation<OrdersSearchByOrderIdRequestDto>> violations =
                validator.validate(request);
        if (!violations.isEmpty()) {
            String messages = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .collect(Collectors.joining("; "));
            log.warn("Request validation failed: {}", messages);
            throw new IllegalArgumentException("Validation failed: " + messages);
        }
    }

    private boolean statusMatches(String orderStatus, String filterStatus) {
        if (filterStatus == null || filterStatus.isBlank()) {
            return true; // no filter — accept all
        }
        return filterStatus.equalsIgnoreCase(orderStatus);
    }

    private OrderDto toDto(Order order) {
        return new OrderDto(
                order.getOrderId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getOrderDate(),
                PiiMaskingUtil.maskName(order.getCustomerName()),
                PiiMaskingUtil.maskEmail(order.getEmail()),
                PiiMaskingUtil.maskAddress(order.getShippingAddress())
        );
    }
}
