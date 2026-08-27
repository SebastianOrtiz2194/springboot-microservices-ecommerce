package com.ecommerce.product.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.product.config.JwtAuthFilter;
import com.ecommerce.product.config.SecurityConfig;
import com.ecommerce.product.domain.Product;
import com.ecommerce.product.dto.CreateProductRequest;
import com.ecommerce.product.dto.ProductResponse;
import com.ecommerce.product.mapper.ProductMapper;
import com.ecommerce.product.service.ProductService;
import jakarta.servlet.FilterChain;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/** Web-layer slice tests for {@link ProductController} with security and validation paths. */
@WebMvcTest(ProductController.class)
@Import(SecurityConfig.class)
class ProductControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private ProductService productService;

    @MockBean private ProductMapper productMapper;

    @MockBean private JwtAuthFilter jwtAuthFilter;

    @BeforeEach
    void passThroughAuthFilter() throws Exception {
        // The real filter parses JWTs; in web slices we let security rely on @WithMockUser
        doAnswer(
                        inv -> {
                            inv.<FilterChain>getArgument(2)
                                    .doFilter(inv.getArgument(0), inv.getArgument(1));
                            return null;
                        })
                .when(jwtAuthFilter)
                .doFilter(any(), any(), any());
    }

    private Product product() {
        Product product = new Product("Laptop", "A laptop", new BigDecimal("999.99"));
        product.setId(1L);
        product.setStockQuantity(10);
        product.setImageUrl("s3-key-1");
        return product;
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createProduct_returnsCreatedWhenAdmin() throws Exception {
        Product product = product();
        when(productMapper.toEntity(any(CreateProductRequest.class))).thenReturn(product);
        when(productService.createProduct(product)).thenReturn(product);
        when(productMapper.toResponse(product))
                .thenReturn(
                        new ProductResponse(
                                1L,
                                "Laptop",
                                "A laptop",
                                new BigDecimal("999.99"),
                                10,
                                "s3-key-1"));

        mockMvc.perform(
                        post("/api/products")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"name":"Laptop","description":"A laptop","price":999.99,"stockQuantity":10}
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Laptop"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createProduct_returnsBadRequestWhenPriceMissing() throws Exception {
        mockMvc.perform(
                        post("/api/products")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"name":"Laptop","description":"A laptop","stockQuantity":10}
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void createProduct_returnsForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(
                        post("/api/products")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"name":"Laptop","price":999.99}
                                        """))
                .andExpect(status().isForbidden());
    }

    @Test
    void getProduct_returnsForbiddenWhenAnonymous() throws Exception {
        mockMvc.perform(get("/api/products/1")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getProduct_returnsProductWithResolvedPresignedUrl() throws Exception {
        Product product = product();
        when(productService.getProduct(1L)).thenReturn(product);
        when(productMapper.toResponse(product))
                .thenReturn(
                        new ProductResponse(
                                1L,
                                "Laptop",
                                "A laptop",
                                new BigDecimal("999.99"),
                                10,
                                "s3-key-1"));
        when(productService.resolveImageUrl("s3-key-1")).thenReturn("https://s3.example/s3-key-1");

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.imageUrl").value("https://s3.example/s3-key-1"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getProduct_returnsBadRequestForNonPositiveId() throws Exception {
        mockMvc.perform(get("/api/products/-5")).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getAllProducts_returnsListWithResolvedUrls() throws Exception {
        Product product = product();
        when(productService.getAllProducts()).thenReturn(java.util.List.of(product));
        when(productMapper.toResponse(product))
                .thenReturn(
                        new ProductResponse(
                                1L,
                                "Laptop",
                                "A laptop",
                                new BigDecimal("999.99"),
                                10,
                                "s3-key-1"));
        when(productService.resolveImageUrl("s3-key-1")).thenReturn("https://s3.example/s3-key-1");

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Laptop"))
                .andExpect(jsonPath("$[0].imageUrl").value("https://s3.example/s3-key-1"));
    }
}
