package com.ecommerce.order.service;

import com.ecommerce.order.event.OrderPlacedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes order events to Kafka for downstream services.
 */
@Service
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;

    public OrderEventPublisher(KafkaTemplate<String, Object> kafkaTemplate,
                               @org.springframework.beans.factory.annotation.Value("${app.kafka.topics.order-placed}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    /**
     * Sends an {@link OrderPlacedEvent} to the configured Kafka topic.
     *
     * @param event the event to publish
     */
    public void publish(OrderPlacedEvent event) {
        log.info("publishing_order_placed order_id={}", event.orderId());
        kafkaTemplate.send(topic, event.orderId().toString(), event);
    }
}
