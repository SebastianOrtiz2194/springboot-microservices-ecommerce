package com.ecommerce.product.repository;

import com.ecommerce.product.domain.ProcessedOrder;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for deduplication tracking — prevents reprocessing the same order. */
public interface ProcessedOrderRepository extends JpaRepository<ProcessedOrder, Long> {

    /**
     * Bulk-deletes deduplication rows processed before the given cutoff.
     *
     * <p>{@code clearAutomatically} detaches entities in the persistence context after the bulk
     * delete so subsequent reads cannot observe rows that no longer exist in the database.
     *
     * @param cutoff rows with {@code processedAt} strictly before this are removed
     * @return number of rows deleted
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ProcessedOrder p where p.processedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
