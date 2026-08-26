package com.ecommerce.order.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link Order} and {@link OrderItem} domain logic. */
class OrderTest {

    @Test
    void addItem_establishesBidirectionalLink() {
        Order order = new Order();
        OrderItem item = new OrderItem(1L, "Laptop", 2, new BigDecimal("10.00"));

        order.addItem(item);

        assertThat(order.getItems()).containsExactly(item);
        assertThat(item.getOrder()).isSameAs(order);
    }

    @Test
    void calculateTotal_sumsSubtotals() {
        Order order = new Order();
        order.addItem(new OrderItem(1L, "Laptop", 2, new BigDecimal("10.00")));
        order.addItem(new OrderItem(2L, "Mouse", 3, new BigDecimal("5.00")));

        order.calculateTotal();

        assertThat(order.getTotalAmount()).isEqualByComparingTo("35.00");
    }

    @Test
    void calculateTotal_emptyOrderIsZero() {
        Order order = new Order();

        order.calculateTotal();

        assertThat(order.getTotalAmount()).isEqualByComparingTo("0");
    }

    @Test
    void getSubtotal_multipliesQuantityByUnitPrice() {
        OrderItem item = new OrderItem(1L, "Laptop", 4, new BigDecimal("12.50"));

        assertThat(item.getSubtotal()).isEqualByComparingTo("50.00");
    }
}
