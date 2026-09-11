package com.ecommerce.product.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecommerce.product.domain.ProcessedOrder;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Repository slice tests for {@link ProcessedOrderRepository} against a real PostgreSQL container,
 * covering the retention bulk-delete used by the cleanup job.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ProcessedOrderRepositoryTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private ProcessedOrderRepository processedOrderRepository;

    @Test
    void deleteOlderThan_removesOnlyExpiredRows() {
        ProcessedOrder expired = new ProcessedOrder(1L);
        expired.setProcessedAt(LocalDateTime.now().minusDays(40));
        processedOrderRepository.saveAndFlush(expired);

        ProcessedOrder recent = new ProcessedOrder(2L);
        processedOrderRepository.saveAndFlush(recent);

        int deleted = processedOrderRepository.deleteOlderThan(LocalDateTime.now().minusDays(30));

        assertThat(deleted).isEqualTo(1);
        assertThat(processedOrderRepository.findById(1L)).isEmpty();
        assertThat(processedOrderRepository.findById(2L)).isPresent();
    }
}
