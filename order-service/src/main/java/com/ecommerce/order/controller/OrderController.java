package com.ecommerce.order.controller;

import com.ecommerce.order.domain.Order;
import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.mapper.OrderMapper;
import com.ecommerce.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for order management. */
@Tag(name = "orders", description = "Order creation and lookup (authenticated users)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/orders")
@Validated
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
     * @param request the validated order payload
     * @param auth the authenticated user's security context
     * @return the created order
     */
    @Operation(summary = "Create an order for the authenticated user")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Order created, event published"),
        @ApiResponse(
                responseCode = "400",
                description = "Validation failed",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "503", description = "Order messaging unavailable")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(
            @Valid @RequestBody CreateOrderRequest request, Authentication auth) {
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
    @Operation(summary = "Get an order by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Order found"),
        @ApiResponse(responseCode = "400", description = "Invalid ID"),
        @ApiResponse(
                responseCode = "404",
                description = "Order not found",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{id}")
    public OrderResponse getOrder(@Positive @PathVariable Long id) {
        return orderMapper.toResponse(orderService.getOrderById(id));
    }

    /**
     * Lists all orders for the authenticated user.
     *
     * @param auth the authenticated user's security context
     * @return list of the user's orders
     */
    @Operation(summary = "List the authenticated user's orders")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Order list")})
    @GetMapping
    public List<OrderResponse> getUserOrders(Authentication auth) {
        Long userId = (Long) auth.getDetails();
        return orderService.getOrdersByUserId(userId).stream()
                .map(orderMapper::toResponse)
                .toList();
    }
}
