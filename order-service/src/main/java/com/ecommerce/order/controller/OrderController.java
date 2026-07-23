package com.ecommerce.order.controller;

import com.ecommerce.order.domain.Order;
import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.mapper.OrderMapper;
import com.ecommerce.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for order management.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderMapper orderMapper;

    public OrderController(OrderService orderService, OrderMapper orderMapper) {
        this.orderService = orderService;
        this.orderMapper = orderMapper;
    }

    /**
     * Creates a new order for the authenticated user.
     *
     * @param request     the validated order payload
     * @param auth        the authenticated user's security context
     * @return the created order
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request, Authentication auth) {
        Long userId = (Long) auth.getDetails();
        Order order = orderService.createOrder(userId, request);
        return orderMapper.toResponse(order);
    }

    /**
     * Retrieves an order by ID.
     *
     * @param id the order identifier
     * @return the order
     */
    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable Long id) {
        return orderMapper.toResponse(orderService.getOrderById(id));
    }

    /**
     * Lists all orders for the authenticated user.
     *
     * @param auth the authenticated user's security context
     * @return list of the user's orders
     */
    @GetMapping
    public List<OrderResponse> getUserOrders(Authentication auth) {
        Long userId = (Long) auth.getDetails();
        return orderService.getOrdersByUserId(userId).stream()
                .map(orderMapper::toResponse)
                .toList();
    }
}
