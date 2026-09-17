package com.arc.orderslambda.service;

import com.arc.orderslambda.dto.OrderDto;
import com.arc.orderslambda.dto.OrdersSearchRequestDto;
import com.arc.orderslambda.dto.OrdersSearchResponseDto;
import com.arc.orderslambda.model.Order;
import com.arc.orderslambda.repository.OrderRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OrdersSearchService}.
 *
 * <p>Coverage targets: ≥ 80 % branch (GP-05).
 * Branches covered:
 * <ul>
 *   <li>Null request → IllegalArgumentException</li>
 *   <li>Blank customerId → validation failure</li>
 *   <li>customerId too long → validation failure</li>
 *   <li>customerId with invalid chars → validation failure</li>
 *   <li>limit out of range (0, 101) → validation failure</li>
 *   <li>Invalid status value → validation failure</li>
 *   <li>No status filter → all statuses returned</li>
 *   <li>Status filter matches → filtered results</li>
 *   <li>Status filter no match → empty result</li>
 *   <li>Repository returns empty list → empty response</li>
 *   <li>PII fields masked in returned DTOs</li>
 *   <li>Limit respected — response does not exceed requested limit</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class OrdersSearchServiceTest {

    @Mock
    private OrderRepository orderRepository;

    private OrdersSearchService service;
    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        service = new OrdersSearchService(orderRepository, validator);
    }

    // ── Validation failure branches ───────────────────────────────────────────

    @Test
    @DisplayName("Null request throws IllegalArgumentException")
    void search_nullRequest_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.search(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null");
    }

    @Test
    @DisplayName("Blank customerId fails validation")
    void search_blankCustomerId_throwsIllegalArgumentException() {
        OrdersSearchRequestDto req = new OrdersSearchRequestDto("", null, 10);
        assertThatThrownBy(() -> service.search(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("customerId");
    }

    @Test
    @DisplayName("customerId exceeding 64 chars fails validation")
    void search_customerIdTooLong_throwsIllegalArgumentException() {
        String longId = "A".repeat(65);
        OrdersSearchRequestDto req = new OrdersSearchRequestDto(longId, null, 10);
        assertThatThrownBy(() -> service.search(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("customerId");
    }

    @Test
    @DisplayName("customerId with invalid characters fails validation")
    void search_customerIdInvalidChars_throwsIllegalArgumentException() {
        OrdersSearchRequestDto req = new OrdersSearchRequestDto("cust@#$%", null, 10);
        assertThatThrownBy(() -> service.search(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("customerId");
    }

    @Test
    @DisplayName("limit = 0 fails validation (min 1)")
    void search_limitZero_throwsIllegalArgumentException() {
        OrdersSearchRequestDto req = new OrdersSearchRequestDto("cust-001", null, 0);
        assertThatThrownBy(() -> service.search(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
    }

    @Test
    @DisplayName("limit = 101 fails validation (max 100)")
    void search_limitOver100_throwsIllegalArgumentException() {
        OrdersSearchRequestDto req = new OrdersSearchRequestDto("cust-001", null, 101);
        assertThatThrownBy(() -> service.search(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
    }

    @Test
    @DisplayName("Invalid status value fails validation")
    void search_invalidStatus_throwsIllegalArgumentException() {
        OrdersSearchRequestDto req = new OrdersSearchRequestDto("cust-001", "UNKNOWN_STATUS", 10);
        assertThatThrownBy(() -> service.search(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("status");
    }

    // ── Happy-path branches ───────────────────────────────────────────────────

    @Test
    @DisplayName("No status filter — all returned orders included regardless of status")
    void search_noStatusFilter_returnsAllOrders() {
        List<Order> repoOrders = List.of(
                buildOrder("ord-1", "cust-001", "PENDING", "2024-03-01",
                        "Alice Smith", "alice@example.com", "10 Main St"),
                buildOrder("ord-2", "cust-001", "SHIPPED", "2024-03-10",
                        "Alice Smith", "alice@example.com", "10 Main St")
        );
        when(orderRepository.findByCustomerId(eq("cust-001"), anyInt())).thenReturn(repoOrders);

        OrdersSearchResponseDto response = service.search(
                new OrdersSearchRequestDto("cust-001", null, 20));

        assertThat(response.getCount()).isEqualTo(2);
        assertThat(response.getOrders()).hasSize(2);
        verify(orderRepository).findByCustomerId("cust-001", 20);
    }

    @Test
    @DisplayName("Status filter SHIPPED — only SHIPPED orders returned")
    void search_statusFilterShipped_returnsOnlyShipped() {
        List<Order> repoOrders = List.of(
                buildOrder("ord-1", "cust-001", "PENDING", "2024-03-01",
                        "Bob Jones", "bob@domain.org", "5 Oak Ave"),
                buildOrder("ord-2", "cust-001", "SHIPPED", "2024-03-10",
                        "Bob Jones", "bob@domain.org", "5 Oak Ave")
        );
        when(orderRepository.findByCustomerId(eq("cust-001"), anyInt())).thenReturn(repoOrders);

        OrdersSearchResponseDto response = service.search(
                new OrdersSearchRequestDto("cust-001", "SHIPPED", 10));

        assertThat(response.getCount()).isEqualTo(1);
        assertThat(response.getOrders().get(0).getOrderId()).isEqualTo("ord-2");
        assertThat(response.getOrders().get(0).getStatus()).isEqualTo("SHIPPED");
    }

    @Test
    @DisplayName("Status filter with no matching orders returns empty response")
    void search_statusFilterNoMatch_returnsEmptyResponse() {
        List<Order> repoOrders = List.of(
                buildOrder("ord-1", "cust-001", "PENDING", "2024-03-01",
                        "Carol White", "carol@mail.net", "8 Pine Rd")
        );
        when(orderRepository.findByCustomerId(eq("cust-001"), anyInt())).thenReturn(repoOrders);

        OrdersSearchResponseDto response = service.search(
                new OrdersSearchRequestDto("cust-001", "DELIVERED", 10));

        assertThat(response.getCount()).isEqualTo(0);
        assertThat(response.getOrders()).isEmpty();
    }

    @Test
    @DisplayName("Repository returns empty list — response count is zero")
    void search_repositoryEmpty_returnsEmptyResponse() {
        when(orderRepository.findByCustomerId(eq("cust-001"), anyInt()))
                .thenReturn(Collections.emptyList());

        OrdersSearchResponseDto response = service.search(
                new OrdersSearchRequestDto("cust-001", null, 5));

        assertThat(response.getCount()).isEqualTo(0);
        assertThat(response.getOrders()).isEmpty();
    }

    @Test
    @DisplayName("PII fields are masked in the returned OrderDto")
    void search_piiMasked_inReturnedDto() {
        List<Order> repoOrders = List.of(
                buildOrder("ord-99", "cust-001", "CONFIRMED", "2024-04-01",
                        "David Lee", "david.lee@corp.com", "22 Elm Street, Boston")
        );
        when(orderRepository.findByCustomerId(eq("cust-001"), anyInt())).thenReturn(repoOrders);

        OrdersSearchResponseDto response = service.search(
                new OrdersSearchRequestDto("cust-001", "CONFIRMED", 10));

        assertThat(response.getCount()).isEqualTo(1);
        OrderDto dto = response.getOrders().get(0);

        // Name: "David Lee" → first char of each word retained
        assertThat(dto.getMaskedCustomerName()).isEqualTo("D**** L**");

        // Email: local "david.lee" → keep last 4 chars = ".lee", prepend *****
        assertThat(dto.getMaskedEmail()).isEqualTo("*****" + ".lee@corp.com");

        // Address: letters masked, digits/punctuation kept
        assertThat(dto.getMaskedShippingAddress()).isEqualTo("22 *** ******, ******");

        // Non-PII fields pass through unchanged
        assertThat(dto.getOrderId()).isEqualTo("ord-99");
        assertThat(dto.getCustomerId()).isEqualTo("cust-001");
        assertThat(dto.getStatus()).isEqualTo("CONFIRMED");
        assertThat(dto.getOrderDate()).isEqualTo("2024-04-01");
    }

    @Test
    @DisplayName("Limit is respected — response does not exceed requested limit")
    void search_limitEnforced_responseDoesNotExceedLimit() {
        List<Order> repoOrders = List.of(
                buildOrder("ord-1", "cust-001", "PENDING", "2024-01-01", "E F", "e@f.com", "1 A"),
                buildOrder("ord-2", "cust-001", "PENDING", "2024-01-02", "E F", "e@f.com", "1 A"),
                buildOrder("ord-3", "cust-001", "PENDING", "2024-01-03", "E F", "e@f.com", "1 A")
        );
        when(orderRepository.findByCustomerId(eq("cust-001"), anyInt())).thenReturn(repoOrders);

        OrdersSearchResponseDto response = service.search(
                new OrdersSearchRequestDto("cust-001", null, 2));

        assertThat(response.getCount()).isEqualTo(2);
        assertThat(response.getOrders()).hasSize(2);
    }

    // ── Test data builder ─────────────────────────────────────────────────────

    private Order buildOrder(String orderId, String customerId, String status,
                             String orderDate, String name, String email, String address) {
        Order o = new Order();
        o.setOrderId(orderId);
        o.setCustomerId(customerId);
        o.setStatus(status);
        o.setOrderDate(orderDate);
        o.setCustomerName(name);
        o.setEmail(email);
        o.setShippingAddress(address);
        return o;
    }
}
