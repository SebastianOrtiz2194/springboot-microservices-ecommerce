package com.ecommerce.order.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.order.event.OrderPlacedEvent;
import com.ecommerce.order.exception.OrderEventPublishException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * Unit tests for {@link OrderEventPublisher}. The {@code @CircuitBreaker} annotation is inert
 * without a Spring proxy, so these cover the raw send logic; failure still surfaces as {@link
 * OrderEventPublishException} which the fallback also throws through the proxy.
 */
@ExtendWith(MockitoExtension.class)
class OrderEventPublisherTest {

    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    private OrderEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OrderEventPublisher(kafkaTemplate, "order-placed");
    }

    private OrderPlacedEvent event() {
        return new OrderPlacedEvent(1L, 7L, List.of(), BigDecimal.TEN, LocalDateTime.now());
    }

    @Test
    void publish_sendsEventToTopic() {
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.complete(null);
        when(kafkaTemplate.send(anyString(), anyString(), any(OrderPlacedEvent.class)))
                .thenReturn(future);

        publisher.publish(event());

        verify(kafkaTemplate).send(eq("order-placed"), eq("1"), any(OrderPlacedEvent.class));
    }

    @Test
    void publish_throwsWhenBrokerFails() {
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("kafka down"));
        when(kafkaTemplate.send(anyString(), anyString(), any(OrderPlacedEvent.class)))
                .thenReturn(future);

        assertThatThrownBy(() -> publisher.publish(event()))
                .isInstanceOf(OrderEventPublishException.class)
                .hasMessageContaining("Failed to publish order event");
    }
}
