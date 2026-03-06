package com.pidabrow.starter.infrastructure.outbox;

import com.pidabrow.starter.common.outbox.MessagePublisher;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Polls the outbox table for PENDING records and relays them to Kafka
 * via {@link MessagePublisher}.
 * <p>
 * Uses ShedLock to prevent concurrent processing by multiple service instances.
 * <p>
 * <strong>Visibility Buffer</strong>: Only fetches records where
 * {@code created_at < NOW() - 1 second} to ensure the originating transaction
 * has been committed and is visible.
 * <p>
 * <strong>Resilience</strong>: Implements exponential backoff via retry count.
 * After {@value #MAX_RETRIES} failed attempts the record status moves to FAILED.
 * <p>
 * This is a package-private infrastructure adapter.
 */
@Component
@ConditionalOnProperty(name = "outbox.enabled", havingValue = "true")
class OutboxRelayService {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayService.class);
    static final int MAX_RETRIES = 5;

    private final MessageOutboxRepository outboxRepository;
    private final MessagePublisher messagePublisher;

    OutboxRelayService(MessageOutboxRepository outboxRepository, MessagePublisher messagePublisher) {
        this.outboxRepository = outboxRepository;
        this.messagePublisher = messagePublisher;
    }

    /**
     * Scheduled relay: polls every 5 seconds, locks for at most 4 minutes,
     * keeps the lock for at least 5 seconds to prevent overlap.
     */
    @Scheduled(fixedDelayString = "${outbox.relay.poll-interval-ms:5000}")
    @SchedulerLock(
            name = "outboxRelay",
            lockAtMostFor = "PT4M",
            lockAtLeastFor = "PT5S"
    )
    @Transactional
    public void relay() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusSeconds(1);
        List<MessageOutboxEntity> pending = outboxRepository.findPendingMessages(cutoff, MAX_RETRIES);

        if (pending.isEmpty()) {
            return;
        }

        log.info("Outbox relay: processing {} pending message(s)", pending.size());

        for (MessageOutboxEntity record : pending) {
            processRecord(record);
        }
    }

    private void processRecord(MessageOutboxEntity record) {
        try {
            messagePublisher.publish(
                    record.getDestination(),
                    record.getPartitionKey(),
                    record.getPayload(),
                    record.getHeaders()
            );
            record.markSent();
            outboxRepository.save(record);
            log.debug("Outbox record sent: id={}, type={}", record.getId(), record.getMessageType());
        } catch (Exception e) {
            record.recordRetryFailure(truncateError(e));
            if (record.hasExceededMaxRetries(MAX_RETRIES)) {
                record.markFailed(truncateError(e));
                log.error("Outbox record permanently failed after {} retries: id={}, type={}",
                        MAX_RETRIES, record.getId(), record.getMessageType(), e);
            } else {
                log.warn("Outbox record retry {}/{} for id={}, type={}",
                        record.getRetryCount(), MAX_RETRIES, record.getId(), record.getMessageType(), e);
            }
            outboxRepository.save(record);
        }
    }

    private String truncateError(Exception e) {
        String message = e.getMessage();
        if (message != null && message.length() > 1000) {
            return message.substring(0, 1000);
        }
        return message;
    }
}
