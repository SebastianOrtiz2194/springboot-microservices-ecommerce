package com.ecommerce.product.event;

import com.ecommerce.product.domain.ProcessedOrder;
import com.ecommerce.product.domain.Product;
import com.ecommerce.product.repository.ProcessedOrderRepository;
import com.ecommerce.product.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Listens for order events and decrements product stock accordingly.
 * Uses a deduplication table to ensure idempotent processing.
 */
@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final ProductRepository productRepository;
    private final ProcessedOrderRepository processedOrderRepository;

    public OrderEventConsumer(ProductRepository productRepository,
                              ProcessedOrderRepository processedOrderRepository) {
        this.productRepository = productRepository;
        this.processedOrderRepository = processedOrderRepository;
    }

    /**
     * Consumes {@link OrderPlacedEvent} and decrements stock for each ordered product.
     * If the order has already been processed, the event is silently skipped (idempotent).
     *
     * @param event the order placed event from Kafka
     */
    @KafkaListener(topics = "${app.kafka.topics.order-placed}", groupId = "product-service")
    @Transactional
    public void handleOrderPlaced(OrderPlacedEvent event) {
        if (processedOrderRepository.existsById(event.orderId())) {
            log.warn("skipping_duplicate_order order_id={}", event.orderId());
            return;
        }

        log.info("received_order_placed order_id={} items={}", event.orderId(), event.items().size());

        for (OrderItemEvent item : event.items()) {
            Product product = productRepository.findById(item.productId())
                    .orElse(null);

            if (product == null) {
                log.warn("product_not_found_in_order order_id={} product_id={}", event.orderId(), item.productId());
                continue;
            }

            int newStock = product.getStockQuantity() != null
                    ? product.getStockQuantity() - item.quantity()
                    : -item.quantity();

            if (newStock < 0) {
                log.warn("insufficient_stock order_id={} product_id={} current={} requested={}",
                        event.orderId(), item.productId(),
                        product.getStockQuantity(), item.quantity());
            }

            product.setStockQuantity(newStock);
            productRepository.save(product);

            log.info("stock_updated product_id={} new_stock={}", item.productId(), newStock);
        }

        try {
            processedOrderRepository.save(new ProcessedOrder(event.orderId()));
        } catch (DataIntegrityViolationException e) {
            log.warn("concurrent_duplicate_detected order_id={} — rolling back", event.orderId());
            throw e;
        }
    }
}
