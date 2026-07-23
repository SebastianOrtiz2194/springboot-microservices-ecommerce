package com.ecommerce.order.service;

import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderItem;
import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.OrderItemRequest;
import com.ecommerce.order.event.OrderItemEvent;
import com.ecommerce.order.event.OrderPlacedEvent;
import com.ecommerce.order.exception.OrderNotFoundException;
import com.ecommerce.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Handles order-related business logic.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;

    public OrderService(OrderRepository orderRepository, OrderEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Creates a new order from the request, persists it, and publishes an event to Kafka.
     *
     * @param userId  the authenticated user's ID from JWT
     * @param request the order payload
     * @return the persisted order with generated ID
     */
    @Transactional
    public Order createOrder(Long userId, CreateOrderRequest request) {
        log.info("create_order user_id={} items={}", userId, request.items().size());

        Order order = new Order();
        order.setUserId(userId);

        for (OrderItemRequest itemReq : request.items()) {
            OrderItem item = new OrderItem(
                    itemReq.productId(),
                    itemReq.productName(),
                    itemReq.quantity(),
                    itemReq.unitPrice()
            );
            order.addItem(item);
        }

        order.calculateTotal();
        Order saved = orderRepository.save(order);

        OrderPlacedEvent event = new OrderPlacedEvent(
                saved.getId(),
                saved.getUserId(),
                saved.getItems().stream()
                        .map(item -> new OrderItemEvent(item.getProductId(), item.getQuantity(), item.getUnitPrice()))
                        .toList(),
                saved.getTotalAmount(),
                saved.getCreatedAt()
        );
        eventPublisher.publish(event);

        log.info("order_created order_id={} total={}", saved.getId(), saved.getTotalAmount());
        return saved;
    }

    /**
     * Retrieves an order by its ID.
     *
     * @param id the order identifier
     * @return the matching order
     * @throws OrderNotFoundException if no order exists
     */
    public Order getOrderById(Long id) {
        log.info("get_order id={}", id);
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    /**
     * Lists all orders for a specific user.
     *
     * @param userId the user identifier
     * @return list of orders
     */
    public List<Order> getOrdersByUserId(Long userId) {
        log.info("get_orders_by_user user_id={}", userId);
        return orderRepository.findByUserId(userId);
    }
}
