package com.ecommerce.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

/**
 * Tracks which orders have already been processed to ensure idempotent event handling. The order_id
 * acts as a deduplication key — a second insert with the same ID will fail.
 */
@Entity
public class ProcessedOrder {

    @Id private Long orderId;

    @Column(nullable = false)
    private LocalDateTime processedAt;

    public ProcessedOrder() {}

    public ProcessedOrder(Long orderId) {
        this.orderId = orderId;
        this.processedAt = LocalDateTime.now();
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }
}
