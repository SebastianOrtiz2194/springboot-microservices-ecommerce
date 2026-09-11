package com.ecommerce.product.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.product.repository.ProcessedOrderRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link ProcessedOrderCleanupJob}. */
@ExtendWith(MockitoExtension.class)
class ProcessedOrderCleanupJobTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-11T03:00:00Z"), ZoneOffset.UTC);

    @Mock private ProcessedOrderRepository processedOrderRepository;

    private ProcessedOrderCleanupJob job;

    @BeforeEach
    void setUp() {
        job = new ProcessedOrderCleanupJob(processedOrderRepository, FIXED_CLOCK, 30);
    }

    @Test
    void cleanup_deletesRowsOlderThanRetentionWindow() {
        when(processedOrderRepository.deleteOlderThan(any())).thenReturn(3);

        job.cleanup();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(processedOrderRepository).deleteOlderThan(cutoff.capture());
        assertThat(cutoff.getValue()).isEqualTo(LocalDateTime.of(2026, 8, 12, 3, 0));
    }

    @Test
    void cleanup_isNoopWhenNothingMatches() {
        when(processedOrderRepository.deleteOlderThan(any())).thenReturn(0);

        job.cleanup();

        verify(processedOrderRepository).deleteOlderThan(any(LocalDateTime.class));
    }
}
