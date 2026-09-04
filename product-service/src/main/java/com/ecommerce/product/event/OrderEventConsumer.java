package com.ecommerce.product.event;

import com.ecommerce.product.domain.ProcessedOrder;
import com.ecommerce.product.domain.Product;
import com.ecommerce.product.exception.InsufficientStockException;
import com.ecommerce.product.repository.ProcessedOrderRepository;
import com.ecommerce.product.repository.ProductRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Listens for order events and decrements product stock accordingly. Uses a deduplication table and
 * atomic DB updates to ensure idempotent and race-free processing (prevents oversell).
 */
@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final ProductRepository productRepository;
    private final ProcessedOrderRepository processedOrderRepository;
    private final MeterRegistry meterRegistry;

    public OrderEventConsumer(
            ProductRepository productRepository,
            ProcessedOrderRepository processedOrderRepository,
            MeterRegistry meterRegistry) {
        this.productRepository = productRepository;
        this.processedOrderRepository = processedOrderRepository;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Consumes {@link OrderPlacedEvent} and decrements stock for each ordered product. Uses atomic
     * {@code UPDATE ... WHERE stock >= qty} to prevent oversell and optimistic locking via
     * {@code @Version}. Evicts Redis caches to avoid stale stock. If the order has already been
     * processed, the event is silently skipped (idempotent).
     *
     * @param event the order placed event from Kafka
     */
    @KafkaListener(topics = "${app.kafka.topics.order-placed}", groupId = "product-service")
    @Transactional
    @CacheEvict(
            cacheNames = {"product", "productList"},
            allEntries = true)
    public void handleOrderPlaced(OrderPlacedEvent event) {
        if (processedOrderRepository.existsById(event.orderId())) {
            log.warn("skipping_duplicate_order order_id={}", event.orderId());
            return;
        }

        log.info(
                "received_order_placed order_id={} items={}",
                event.orderId(),
                event.items().size());

        for (OrderItemEvent item : event.items()) {
            if (item.quantity() <= 0) {
                log.warn(
                        "invalid_quantity order_id={} product_id={} qty={}",
                        event.orderId(),
                        item.productId(),
                        item.quantity());
                continue;
            }

            // Atomic decrement — succeeds only if sufficient stock
            int updated = productRepository.decrementStock(item.productId(), item.quantity());
            if (updated == 1) {
                log.info(
                        "stock_decremented product_id={} qty={}",
                        item.productId(),
                        item.quantity());
                meterRegistry.counter("stock_decrement_total").increment();
                continue;
            }

            // Decrement failed — check why (missing product vs insufficient stock)
            Product product = productRepository.findById(item.productId()).orElse(null);
            if (product == null) {
                log.warn(
                        "product_not_found_in_order order_id={} product_id={}",
                        event.orderId(),
                        item.productId());
                continue;
            }

            int current = product.getStockQuantity() != null ? product.getStockQuantity() : 0;
            if (current < item.quantity()) {
                log.warn(
                        "insufficient_stock order_id={} product_id={} current={} requested={}",
                        event.orderId(),
                        item.productId(),
                        current,
                        item.quantity());
                // Keep stock unchanged (atomic update already prevented negative)
                // Optionally publish compensating event; for now just warn
                continue;
            }

            // Should not reach here, but handle version conflict fallback with pessimistic lock
            try {
                Product locked = productRepository.findByIdForUpdate(item.productId()).orElse(null);
                if (locked != null) {
                    int lockedStock =
                            locked.getStockQuantity() != null ? locked.getStockQuantity() : 0;
                    if (lockedStock >= item.quantity()) {
                        locked.setStockQuantity(lockedStock - item.quantity());
                        productRepository.save(locked);
                        meterRegistry.counter("stock_decrement_total").increment();
                        log.info(
                                "stock_updated_via_lock product_id={} new_stock={}",
                                item.productId(),
                                locked.getStockQuantity());
                    } else {
                        log.warn(
                                "insufficient_stock_after_lock order_id={} product_id={} current={} requested={}",
                                event.orderId(),
                                item.productId(),
                                lockedStock,
                                item.quantity());
                    }
                }
            } catch (Exception ex) {
                log.error(
                        "stock_update_failed product_id={} order_id={}",
                        item.productId(),
                        event.orderId(),
                        ex);
                throw new InsufficientStockException(
                        "Failed to update stock for product "
                                + item.productId()
                                + ": "
                                + ex.getMessage());
            }
        }

        try {
            processedOrderRepository.save(new ProcessedOrder(event.orderId()));
        } catch (DataIntegrityViolationException e) {
            log.warn("concurrent_duplicate_detected order_id={} — rolling back", event.orderId());
            throw e;
        }
    }
}
