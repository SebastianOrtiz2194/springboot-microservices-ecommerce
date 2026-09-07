package com.ecommerce.order.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/** OpenAPI documentation metadata for order-service: order creation and lookup endpoints. */
@Configuration
@OpenAPIDefinition(
        info =
                @Info(
                        title = "Order Service API",
                        version = "1.0.0",
                        description = "Order creation and lookup endpoints",
                        contact = @Contact(name = "E-Commerce Platform")),
        servers = @Server(url = "http://localhost:8083", description = "order-service (direct)"))
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class OpenApiConfig {}
