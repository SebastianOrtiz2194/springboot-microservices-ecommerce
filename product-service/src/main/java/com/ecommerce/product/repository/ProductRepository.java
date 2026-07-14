package com.ecommerce.product.repository;

import com.ecommerce.product.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link com.ecommerce.product.domain.Product} entities.
 */
public interface ProductRepository extends JpaRepository<Product, Long> {
}
