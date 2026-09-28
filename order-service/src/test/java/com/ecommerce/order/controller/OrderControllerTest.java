package com.ecommerce.order.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.order.config.JwtAuthFilter;
import com.ecommerce.order.config.SecurityConfig;
import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderItem;
import com.ecommerce.order.domain.OrderStatus;
import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.exception.OrderNotFoundException;
import com.ecommerce.order.mapper.OrderMapper;
import com.ecommerce.order.service.OrderService;
import jakarta.servlet.FilterChain;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer slice tests for {@link OrderController} with security context, ownership scoping and
 * validation paths.
 */
@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
class OrderControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private OrderService orderService;

    @MockBean private OrderMapper orderMapper;

    @MockBean private JwtAuthFilter jwtAuthFilter;

    @BeforeEach
    void passThroughAuthFilter() throws Exception {
        // The real filter parses JWTs; in web slices we build the authentication directly
        doAnswer(
                        inv -> {
                            inv.<FilterChain>getArgument(2)
                                    .doFilter(inv.getArgument(0), inv.getArgument(1));
                            return null;
                        })
                .when(jwtAuthFilter)
                .doFilter(any(), any(), any());
    }

    /** Mirrors production: the JWT filter stores the user id in {@code auth.getDetails()}. */
    private static Authentication auth(Long userId, String role) {
        UsernamePasswordAuthenticationToken token =
                new UsernamePasswordAuthenticationToken(
                        "user-" + userId + "@example.com",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        token.setDetails(userId);
        return token;
    }

    private static Order order(Long id, Long ownerId) {
        Order order = new Order();
        order.setId(id);
        order.setUserId(ownerId);
        order.addItem(new OrderItem(1L, "Laptop", 2, new BigDecimal("10.00")));
        order.calculateTotal();
        return order;
    }

    private static OrderResponse response(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getTotalAmount(),
                OrderStatus.CREATED,
                null,
                List.of());
    }

    @Test
    void getOrder_returnsOwnOrderForRequester() throws Exception {
        Order order = order(1L, 7L);
        when(orderService.getOrderById(1L, 7L, false)).thenReturn(order);
        when(orderMapper.toResponse(order)).thenReturn(response(order));

        mockMvc.perform(get("/api/orders/1").with(authentication(auth(7L, "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.userId").value(7));
    }

    @Test
    void getOrder_hidesForeignOrderAsNotFound() throws Exception {
        when(orderService.getOrderById(1L, 99L, false)).thenThrow(new OrderNotFoundException(1L));

        mockMvc.perform(get("/api/orders/1").with(authentication(auth(99L, "USER"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Order not found"));
    }

    @Test
    void getOrder_allowsAdminToReadAnyOrder() throws Exception {
        Order order = order(1L, 7L);
        when(orderService.getOrderById(1L, 99L, true)).thenReturn(order);
        when(orderMapper.toResponse(order)).thenReturn(response(order));

        mockMvc.perform(get("/api/orders/1").with(authentication(auth(99L, "ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(7));
    }

    @Test
    void getOrder_returnsForbiddenWhenAnonymous() throws Exception {
        mockMvc.perform(get("/api/orders/1")).andExpect(status().isForbidden());
    }

    @Test
    void getOrder_returnsBadRequestForNonPositiveId() throws Exception {
        mockMvc.perform(get("/api/orders/0").with(authentication(auth(7L, "USER"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOrder_returnsCreatedForUser() throws Exception {
        Order order = order(5L, 7L);
        when(orderService.createOrder(eq(7L), any(CreateOrderRequest.class))).thenReturn(order);
        when(orderMapper.toResponse(order)).thenReturn(response(order));

        mockMvc.perform(
                        post("/api/orders")
                                .with(authentication(auth(7L, "USER")))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"items":[{"productId":1,"productName":"Laptop","quantity":2,"unitPrice":10.00}]}
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.userId").value(7));
    }

    @Test
    void createOrder_returnsBadRequestForEmptyItems() throws Exception {
        mockMvc.perform(
                        post("/api/orders")
                                .with(authentication(auth(7L, "USER")))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    @Test
    void getUserOrders_returnsOnlyCallerOrders() throws Exception {
        Order order = order(5L, 7L);
        when(orderService.getOrdersByUserId(7L)).thenReturn(List.of(order));
        when(orderMapper.toResponse(order)).thenReturn(response(order));

        mockMvc.perform(get("/api/orders").with(authentication(auth(7L, "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(7));
    }
}
