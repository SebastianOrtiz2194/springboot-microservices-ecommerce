package com.ecommerce.product.repository;

import com.ecommerce.product.domain.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Spring Data repository for {@link com.ecommerce.product.domain.Product} entities.
 */
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Finds a product with a pessimistic write lock for safe stock updates.
     *
     * @param id the product id
     * @return optional product with lock
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    /**
     * Atomically decrements stock if sufficient quantity exists.
     *
     * @param id  product id
     * @param qty quantity to decrement
     * @return number of rows updated (0 if insufficient stock)
     */
    @Modifying
    @Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity - :qty WHERE p.id = :id AND p.stockQuantity >= :qty")
    int decrementStock(@Param("id") Long id, @Param("qty") int qty);

    Page<Product> findAll(Pageable pageable);
}
