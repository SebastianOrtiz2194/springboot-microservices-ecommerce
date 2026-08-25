package com.ecommerce.product.repository;

import com.ecommerce.product.domain.ProcessedOrder;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for deduplication tracking — prevents reprocessing the same order. */
public interface ProcessedOrderRepository extends JpaRepository<ProcessedOrder, Long> {}
