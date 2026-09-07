package com.ecommerce.product.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/** OpenAPI documentation metadata for product-service: catalog and image upload endpoints. */
@Configuration
@OpenAPIDefinition(
        info =
                @Info(
                        title = "Product Service API",
                        version = "1.0.0",
                        description = "Product catalog, stock and image upload endpoints",
                        contact = @Contact(name = "E-Commerce Platform")),
        servers = @Server(url = "http://localhost:8082", description = "product-service (direct)"))
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class OpenApiConfig {}
