package com.ecommerce.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.order.domain.Order;
import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.OrderItemRequest;
import com.ecommerce.order.event.OrderPlacedEvent;
import com.ecommerce.order.exception.OrderNotFoundException;
import com.ecommerce.order.repository.OrderRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link OrderService}. */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;

    @Mock private OrderEventPublisher eventPublisher;

    private MeterRegistry meterRegistry;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        orderService = new OrderService(orderRepository, eventPublisher, meterRegistry);
    }

    private CreateOrderRequest request() {
        return new CreateOrderRequest(
                List.of(
                        new OrderItemRequest(1L, "Laptop", 2, new BigDecimal("10.00")),
                        new OrderItemRequest(2L, "Mouse", 3, new BigDecimal("5.00"))));
    }

    @Test
    void createOrder_persistsOrderCalculatesTotalAndPublishesEvent() {
        CreateOrderRequest request = request();
        when(orderRepository.save(any(Order.class)))
                .thenAnswer(
                        inv -> {
                            Order order = inv.getArgument(0);
                            order.setId(1L);
                            return order;
                        });

        Order saved = orderService.createOrder(7L, request);

        assertThat(saved.getId()).isEqualTo(1L);
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getItems()).hasSize(2);
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("35.00");

        ArgumentCaptor<OrderPlacedEvent> eventCaptor =
                ArgumentCaptor.forClass(OrderPlacedEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        OrderPlacedEvent event = eventCaptor.getValue();
        assertThat(event.orderId()).isEqualTo(1L);
        assertThat(event.userId()).isEqualTo(7L);
        assertThat(event.items()).hasSize(2);
        assertThat(event.totalAmount()).isEqualByComparingTo("35.00");

        assertThat(meterRegistry.get("orders_created_total").counter().count()).isEqualTo(1.0);
    }

    @Test
    void createOrder_emptyItemListTotalsZero() {
        CreateOrderRequest request = new CreateOrderRequest(List.of());
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order saved = orderService.createOrder(7L, request);

        assertThat(saved.getTotalAmount()).isEqualByComparingTo("0");
        assertThat(meterRegistry.get("orders_created_total").counter().count()).isEqualTo(1.0);
    }

    @Test
    void getOrderById_returnsOrderWhenFound() {
        Order order = new Order();
        order.setId(1L);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        Order result = orderService.getOrderById(1L);

        assertThat(result).isSameAs(order);
    }

    @Test
    void getOrderById_throwsWhenNotFound() {
        when(orderRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(42L))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("42");
    }

    @Test
    void getOrdersByUserId_returnsUserOrders() {
        Order order = new Order();
        when(orderRepository.findByUserId(7L)).thenReturn(List.of(order));

        List<Order> result = orderService.getOrdersByUserId(7L);

        assertThat(result).containsExactly(order);
    }
}
