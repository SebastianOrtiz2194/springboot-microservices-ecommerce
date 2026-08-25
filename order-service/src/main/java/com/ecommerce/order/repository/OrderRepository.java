package com.ecommerce.order.repository;

import com.ecommerce.order.domain.Order;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link Order} entities. */
public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserId(Long userId);
}
