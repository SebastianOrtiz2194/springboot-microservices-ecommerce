package com.ecommerce.product.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecommerce.product.domain.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Repository slice tests for {@link ProductRepository} against a real PostgreSQL container with
 * Flyway migrations applied. Covers the atomic stock decrement query.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ProductRepositoryTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private ProductRepository productRepository;

    @Test
    void findById_returnsSeededProduct() {
        assertThat(productRepository.findById(1L))
                .map(Product::getName)
                .contains("Wireless Headphones");
    }

    @Test
    void decrementStock_succeedsWhenSufficientStock() {
        int updated = productRepository.decrementStock(1L, 30);

        assertThat(updated).isEqualTo(1);
        Product refreshed = productRepository.findById(1L).orElseThrow();
        assertThat(refreshed.getStockQuantity()).isEqualTo(70);
    }

    @Test
    void decrementStock_returnsZeroWhenInsufficientStock() {
        int updated = productRepository.decrementStock(1L, 9999);

        assertThat(updated).isZero();
        Product refreshed = productRepository.findById(1L).orElseThrow();
        assertThat(refreshed.getStockQuantity()).isEqualTo(100);
    }

    @Test
    void findByIdForUpdate_returnsProduct() {
        assertThat(productRepository.findByIdForUpdate(1L)).isPresent();
    }

    @Test
    void findByIdForUpdate_emptyForUnknownId() {
        assertThat(productRepository.findByIdForUpdate(9999L)).isEmpty();
    }
}
