package com.pidabrow.starter.infrastructure.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link MessageOutboxEntity}.
 * Package-private — only used by infrastructure adapters in this package.
 */
interface MessageOutboxRepository extends JpaRepository<MessageOutboxEntity, UUID> {

    /**
     * Fetches PENDING records with a visibility buffer to ensure transaction visibility.
     * Only fetches records created before the given cutoff time.
     *
     * @param cutoff    records must have been created before this time
     * @param maxRetries maximum retries allowed before a record is considered failed
     * @return batch of pending outbox records
     */
    @Query("""
            SELECT m FROM MessageOutboxEntity m
            WHERE m.status = 'PENDING'
              AND m.createdAt < :cutoff
              AND m.retryCount < :maxRetries
            ORDER BY m.createdAt ASC
            LIMIT 100
            """)
    List<MessageOutboxEntity> findPendingMessages(
            @Param("cutoff") OffsetDateTime cutoff,
            @Param("maxRetries") int maxRetries);

    /**
     * Deletes SENT records older than the given retention cutoff.
     *
     * @param retentionCutoff records processed before this time will be purged
     * @return number of deleted records
     */
    @Modifying
    @Query("""
            DELETE FROM MessageOutboxEntity m
            WHERE m.status = 'SENT'
              AND m.processedAt < :retentionCutoff
            """)
    int deleteSentRecordsOlderThan(@Param("retentionCutoff") OffsetDateTime retentionCutoff);
}

