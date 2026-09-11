package com.ecommerce.product.event;

import com.ecommerce.product.repository.ProcessedOrderRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prunes the {@code processed_order} deduplication table so it does not grow without bound.
 *
 * <p>Safe by design: Kafka's own retention (default 7 days) means the broker will not redeliver an
 * event older than that, so dedupe rows older than the configured retention window (default 30
 * days) can never be needed again. A consumer offset reset to {@code earliest} within the window
 * still finds every relevant key.
 */
@Component
public class ProcessedOrderCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(ProcessedOrderCleanupJob.class);

    private final ProcessedOrderRepository processedOrderRepository;
    private final Clock clock;
    private final int retentionDays;

    public ProcessedOrderCleanupJob(
            ProcessedOrderRepository processedOrderRepository,
            Clock clock,
            @Value("${app.kafka.processed-order.retention-days:30}") int retentionDays) {
        this.processedOrderRepository = processedOrderRepository;
        this.clock = clock;
        this.retentionDays = retentionDays;
    }

    /** Deletes dedupe rows older than the retention window. Runs daily at 03:00 by default. */
    @Scheduled(cron = "${app.kafka.processed-order.cleanup-cron:0 0 3 * * *}")
    @Transactional
    public void cleanup() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(retentionDays);
        int deleted = processedOrderRepository.deleteOlderThan(cutoff);
        if (deleted > 0) {
            log.info("processed_order_cleanup deleted={} cutoff={}", deleted, cutoff);
        }
    }
}
