package com.ecommerce.order.service;

import com.ecommerce.order.event.OrderPlacedEvent;
import com.ecommerce.order.exception.OrderEventPublishException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes order events to Kafka for downstream services. Guarded by a circuit breaker so a Kafka
 * outage fails fast instead of piling up blocked threads. The send is blocking (bounded by a
 * timeout) so broker failures surface synchronously and roll back the order transaction.
 */
@Service
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private static final Duration PUBLISH_TIMEOUT = Duration.ofSeconds(5);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;

    public OrderEventPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.kafka.topics.order-placed}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    /**
     * Sends an {@link OrderPlacedEvent} to the configured Kafka topic.
     *
     * @param event the event to publish
     * @throws OrderEventPublishException if the broker is unreachable or the circuit is open
     */
    @CircuitBreaker(name = "orderEventPublisher", fallbackMethod = "publishFallback")
    public void publish(OrderPlacedEvent event) {
        log.info("publishing_order_placed order_id={}", event.orderId());
        try {
            kafkaTemplate
                    .send(topic, event.orderId().toString(), event)
                    .get(PUBLISH_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OrderEventPublishException("Interrupted while publishing order event", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new OrderEventPublishException("Failed to publish order event", e);
        }
    }

    private void publishFallback(OrderPlacedEvent event, Exception ex) {
        log.error(
                "order_event_publish_failed order_id={} reason={}", event.orderId(), ex.toString());
        throw new OrderEventPublishException(
                "Order messaging temporarily unavailable — please retry", ex);
    }
}
