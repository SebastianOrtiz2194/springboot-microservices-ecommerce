package com.ecommerce.order.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderItem;
import com.ecommerce.order.domain.OrderStatus;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Repository slice tests for {@link OrderRepository} against a real PostgreSQL container with
 * Flyway migrations applied. Verifies cascade persistence of order items.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class OrderRepositoryTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private OrderRepository orderRepository;

    private Order orderFor(long userId) {
        Order order = new Order();
        order.setUserId(userId);
        order.addItem(new OrderItem(1L, "Laptop", 2, new BigDecimal("10.00")));
        order.addItem(new OrderItem(2L, "Mouse", 1, new BigDecimal("5.00")));
        order.calculateTotal();
        return order;
    }

    @Test
    void save_persistsOrderWithCascadedItems() {
        Order saved = orderRepository.save(orderFor(7L));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("25.00");
        assertThat(saved.getItems()).hasSize(2);
        assertThat(saved.getItems()).allSatisfy(item -> assertThat(item.getId()).isNotNull());
    }

    @Test
    void findByUserId_returnsOnlyThatUsersOrders() {
        orderRepository.save(orderFor(7L));
        orderRepository.save(orderFor(8L));

        List<Order> orders = orderRepository.findByUserId(7L);

        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).getUserId()).isEqualTo(7L);
    }

    @Test
    void findById_returnsOrderWithItems() {
        Order saved = orderRepository.save(orderFor(7L));

        Order found = orderRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getItems()).hasSize(2);
    }
}
