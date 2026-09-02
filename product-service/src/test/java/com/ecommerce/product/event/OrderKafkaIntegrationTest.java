package com.ecommerce.product.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ecommerce.product.domain.Product;
import com.ecommerce.product.repository.ProcessedOrderRepository;
import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.product.service.ProductService;
import com.ecommerce.product.service.S3Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Integration test of the order-placed event flow against real PostgreSQL, Redis, and Kafka
 * containers: event is consumed, stock is decremented atomically, duplicates are skipped, and
 * insufficient stock never goes negative.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class OrderKafkaIntegrationTest {

    private static final String TOPIC = "order-placed";

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    @Container static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.1");

    @DynamicPropertySource
    static void registerInfraProperties(DynamicPropertyRegistry registry) {
        // Boot 3.3 has no @ServiceConnection factories for Kafka/Redis — wire them explicitly
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @MockBean private S3Service s3Service;

    @SpyBean private ProductRepository productRepository;

    @Autowired private ProcessedOrderRepository processedOrderRepository;

    @Autowired private ProductService productService;

    @Autowired private KafkaTemplate<String, Object> kafkaTemplate;

    private OrderPlacedEvent event(long orderId, long productId, int quantity) {
        return new OrderPlacedEvent(
                orderId,
                7L,
                List.of(new OrderItemEvent(productId, quantity, new BigDecimal("9.99"))),
                new BigDecimal("9.99").multiply(BigDecimal.valueOf(quantity)),
                LocalDateTime.now());
    }

    @Test
    void orderPlacedEvent_decrementsStock() throws Exception {
        assertStock(1L, 100);

        kafkaTemplate.send(TOPIC, "9001", event(9001L, 1L, 30)).get(10, TimeUnit.SECONDS);

        awaitStock(1L, 70);
    }

    @Test
    void duplicateOrderPlacedEvent_isSkippedIdempotently() throws Exception {
        assertStock(2L, 50);

        kafkaTemplate.send(TOPIC, "9002", event(9002L, 2L, 5)).get(10, TimeUnit.SECONDS);
        awaitStock(2L, 45);

        kafkaTemplate.send(TOPIC, "9002", event(9002L, 2L, 5)).get(10, TimeUnit.SECONDS);
        awaitProcessed(9002L);
        Thread.sleep(1_500);

        assertStock(2L, 45);
    }

    @Test
    void insufficientStock_neverGoesNegative() throws Exception {
        assertStock(3L, 200);

        kafkaTemplate.send(TOPIC, "9003", event(9003L, 3L, 999_999)).get(10, TimeUnit.SECONDS);

        awaitProcessed(9003L);
        assertStock(3L, 200);
    }

    @Test
    void productLookup_servesSecondReadFromRedisCache() {
        Product first = productService.getProduct(4L);
        assertThat(first).isNotNull();

        Product second = productService.getProduct(4L);
        assertThat(second).isNotNull();

        verify(productRepository, times(1)).findById(4L);
    }

    private void assertStock(long productId, int expected) {
        assertThat(productRepository.findById(productId))
                .map(Product::getStockQuantity)
                .contains(expected);
    }

    private void awaitStock(long productId, int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        Integer current = null;
        while (System.currentTimeMillis() < deadline) {
            current =
                    productRepository
                            .findById(productId)
                            .map(Product::getStockQuantity)
                            .orElse(null);
            if (current != null && current == expected) {
                return;
            }
            Thread.sleep(300);
        }
        fail("stock for product " + productId + " did not reach " + expected + ", was " + current);
    }

    private void awaitProcessed(long orderId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            if (processedOrderRepository.existsById(orderId)) {
                return;
            }
            Thread.sleep(300);
        }
        fail("order " + orderId + " was not marked processed");
    }
}
