package com.pidabrow.starter.infrastructure.outbox;

import com.pidabrow.starter.common.outbox.MessagePublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxRelayService Unit Tests")
class OutboxRelayServiceTest {

    @Mock
    private MessageOutboxRepository outboxRepository;

    @Mock
    private MessagePublisher messagePublisher;

    private OutboxRelayService relayService;

    @BeforeEach
    void setUp() {
        relayService = new OutboxRelayService(outboxRepository, messagePublisher, 100);
    }

    @Test
    @DisplayName("Should publish message and mark record SENT on success")
    void should_publish_and_mark_sent_on_success() {
        // Given
        MessageOutboxEntity record = pendingRecord();
        when(outboxRepository.findPendingMessages(any(), eq(OutboxRelayService.MAX_RETRIES), any(), eq(MessageOutboxStatus.PENDING)))
                .thenReturn(List.of(record));

        // When
        relayService.relay();

        // Then
        verify(messagePublisher).publish(
                eq(record.getDestination()),
                eq(record.getPartitionKey()),
                eq(record.getPayload()),
                eq(record.getHeaders())
        );
        assertThat(record.getStatus()).isEqualTo(MessageOutboxStatus.SENT);
        assertThat(record.getProcessedAt()).isNotNull();
        verify(outboxRepository).save(record);
    }

    @Test
    @DisplayName("Should increment retry count and keep PENDING on first failure")
    void should_increment_retry_count_on_failure() {
        // Given
        MessageOutboxEntity record = pendingRecord();
        when(outboxRepository.findPendingMessages(any(), eq(OutboxRelayService.MAX_RETRIES), any(), eq(MessageOutboxStatus.PENDING)))
                .thenReturn(List.of(record));
        doThrow(new RuntimeException("Kafka unavailable"))
                .when(messagePublisher).publish(any(), any(), any(), any());

        // When
        relayService.relay();

        // Then
        assertThat(record.getStatus()).isEqualTo(MessageOutboxStatus.PENDING);
        assertThat(record.getRetryCount()).isEqualTo(1);
        assertThat(record.getLastError()).contains("Kafka unavailable");
        verify(outboxRepository).save(record);
    }

    @Test
    @DisplayName("Should mark record FAILED after exceeding max retries")
    void should_mark_failed_after_max_retries() {
        // Given: a record that has already been retried MAX_RETRIES-1 times
        MessageOutboxEntity record = pendingRecord();
        for (int i = 0; i < OutboxRelayService.MAX_RETRIES - 1; i++) {
            record.recordRetryFailure("previous error");
        }
        when(outboxRepository.findPendingMessages(any(), eq(OutboxRelayService.MAX_RETRIES), any(), eq(MessageOutboxStatus.PENDING)))
                .thenReturn(List.of(record));
        doThrow(new RuntimeException("Final failure"))
                .when(messagePublisher).publish(any(), any(), any(), any());

        // When
        relayService.relay();

        // Then
        assertThat(record.getStatus()).isEqualTo(MessageOutboxStatus.FAILED);
        assertThat(record.getRetryCount()).isEqualTo(OutboxRelayService.MAX_RETRIES);
        assertThat(record.getLastError()).contains("Final failure");
        verify(outboxRepository).save(record);
    }

    @Test
    @DisplayName("Should process all records in a batch even if some fail")
    void should_process_all_records_even_if_some_fail() {
        // Given
        MessageOutboxEntity successRecord = pendingRecord();
        MessageOutboxEntity failRecord = pendingRecord();
        when(outboxRepository.findPendingMessages(any(), eq(OutboxRelayService.MAX_RETRIES), any(), eq(MessageOutboxStatus.PENDING)))
                .thenReturn(List.of(successRecord, failRecord));

        // First call succeeds, second throws
        doNothing()
                .doThrow(new RuntimeException("fail"))
                .when(messagePublisher).publish(any(), any(), any(), any());

        // When
        relayService.relay();

        // Then
        assertThat(successRecord.getStatus()).isEqualTo(MessageOutboxStatus.SENT);
        assertThat(failRecord.getStatus()).isEqualTo(MessageOutboxStatus.PENDING);
        verify(outboxRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("Should do nothing when no pending records exist")
    void should_do_nothing_when_no_pending_records() {
        // Given
        when(outboxRepository.findPendingMessages(any(), eq(OutboxRelayService.MAX_RETRIES), any(), eq(MessageOutboxStatus.PENDING)))
                .thenReturn(List.of());

        // When
        relayService.relay();

        // Then
        verifyNoInteractions(messagePublisher);
        verify(outboxRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should use configured batch size in query")
    void should_use_configured_batch_size() {
        // Given
        int customBatchSize = 50;
        relayService = new OutboxRelayService(outboxRepository, messagePublisher, customBatchSize);
        when(outboxRepository.findPendingMessages(any(), eq(OutboxRelayService.MAX_RETRIES), any(), eq(MessageOutboxStatus.PENDING)))
                .thenReturn(List.of());

        // When
        relayService.relay();

        // Then
        ArgumentCaptor<PageRequest> pageableCaptor = ArgumentCaptor.forClass(PageRequest.class);
        verify(outboxRepository).findPendingMessages(any(), eq(OutboxRelayService.MAX_RETRIES), pageableCaptor.capture(), eq(MessageOutboxStatus.PENDING));
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(customBatchSize);
    }

    @Test
    @DisplayName("Should truncate error message longer than 1000 characters")
    void should_truncate_long_error_messages() {
        // Given
        MessageOutboxEntity record = pendingRecord();
        when(outboxRepository.findPendingMessages(any(), eq(OutboxRelayService.MAX_RETRIES), any(), eq(MessageOutboxStatus.PENDING)))
                .thenReturn(List.of(record));
        String longError = "x".repeat(2000);
        doThrow(new RuntimeException(longError))
                .when(messagePublisher).publish(any(), any(), any(), any());

        // When
        relayService.relay();

        // Then
        assertThat(record.getLastError()).hasSize(1000);
    }

    // --- Helpers ---

    private MessageOutboxEntity pendingRecord() {
        return MessageOutboxEntity.create(
                UUID.randomUUID(),
                "USER_CREATED",
                "com.pidabrow.starter.common.event.UserCreatedEvent",
                "domain-events",
                UUID.randomUUID().toString(),
                Map.of("entityId", UUID.randomUUID().toString()),
                Map.of("x-tenant-id", UUID.randomUUID().toString(),
                        "x-message-type", "USER_CREATED",
                        "x-correlation-id", UUID.randomUUID().toString())
        );
    }
}
