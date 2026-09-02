package com.ecommerce.order.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.ecommerce.order.dto.CreateOrderRequest;
import com.ecommerce.order.dto.OrderItemRequest;
import com.ecommerce.order.service.OrderService;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Integration test proving that {@link OrderService#createOrder} persists an order and publishes a
 * valid {@link OrderPlacedEvent} onto the Kafka topic, using real PostgreSQL and Kafka containers.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class OrderEventPublishingIntegrationTest {

    private static final String TOPIC = "order-placed";

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.1");

    @DynamicPropertySource
    static void registerInfraProperties(DynamicPropertyRegistry registry) {
        // Boot 3.3 has no @ServiceConnection factory for Kafka — wire it explicitly
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired private OrderService orderService;

    @Test
    void createOrder_persistsAndPublishesOrderPlacedEvent() {
        CreateOrderRequest request =
                new CreateOrderRequest(
                        List.of(
                                new OrderItemRequest(1L, "Laptop", 2, new BigDecimal("10.00")),
                                new OrderItemRequest(2L, "Mouse", 3, new BigDecimal("5.00"))));

        var order = orderService.createOrder(42L, request);

        assertThat(order.getId()).isNotNull();
        assertThat(order.getTotalAmount()).isEqualByComparingTo("35.00");

        try (KafkaConsumer<String, String> consumer = verifierConsumer()) {
            consumer.subscribe(List.of(TOPIC));

            String value = pollForOrderEvent(consumer, order.getId());
            assertThat(value)
                    .contains("\"orderId\":" + order.getId())
                    .contains("\"userId\":42")
                    .contains("\"productId\":1")
                    .contains("\"quantity\":2");
        }
    }

    private KafkaConsumer<String, String> verifierConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-verifier-" + System.nanoTime());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(props);
    }

    private String pollForOrderEvent(KafkaConsumer<String, String> consumer, Long orderId) {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            for (var record : consumer.poll(Duration.ofSeconds(1))) {
                if (record.value().contains("\"orderId\":" + orderId)) {
                    return record.value();
                }
            }
        }
        fail("no order-placed event found on topic for order " + orderId);
        return null;
    }
}
