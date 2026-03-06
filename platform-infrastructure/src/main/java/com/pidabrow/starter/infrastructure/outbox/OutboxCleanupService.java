package com.pidabrow.starter.infrastructure.outbox;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Retention policy: purges SENT outbox records older than 7 days.
 * <p>
 * Runs daily and uses ShedLock to prevent concurrent execution.
 * <p>
 * This is a package-private infrastructure adapter.
 */
@Component
@ConditionalOnProperty(name = "outbox.enabled", havingValue = "true")
class OutboxCleanupService {

    private static final Logger log = LoggerFactory.getLogger(OutboxCleanupService.class);
    private static final int RETENTION_DAYS = 7;

    private final MessageOutboxRepository outboxRepository;

    OutboxCleanupService(MessageOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    /**
     * Runs once per day. Deletes SENT records older than {@value #RETENTION_DAYS} days.
     */
    @Scheduled(cron = "${outbox.cleanup.cron:0 0 3 * * *}")
    @SchedulerLock(
            name = "outboxCleanup",
            lockAtMostFor = "PT30M",
            lockAtLeastFor = "PT5M"
    )
    @Transactional
    public void cleanup() {
        OffsetDateTime retentionCutoff = OffsetDateTime.now().minusDays(RETENTION_DAYS);
        int deleted = outboxRepository.deleteSentRecordsOlderThan(retentionCutoff);
        if (deleted > 0) {
            log.info("Outbox cleanup: purged {} SENT record(s) older than {} days", deleted, RETENTION_DAYS);
        }
    }
}
