package com.pidabrow.starter.infrastructure.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxCleanupService Unit Tests")
class OutboxCleanupServiceTest {

    @Mock
    private MessageOutboxRepository outboxRepository;

    private OutboxCleanupService cleanupService;

    @BeforeEach
    void setUp() {
        cleanupService = new OutboxCleanupService(outboxRepository);
    }

    @Test
    @DisplayName("Should delete SENT records older than 7 days")
    void should_delete_sent_records_older_than_7_days() {
        // Given
        when(outboxRepository.deleteSentRecordsOlderThan(any(), eq(MessageOutboxStatus.SENT))).thenReturn(5);

        // When
        cleanupService.cleanup();

        // Then: cutoff should be approximately 7 days ago
        ArgumentCaptor<OffsetDateTime> cutoffCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(outboxRepository).deleteSentRecordsOlderThan(cutoffCaptor.capture(), eq(MessageOutboxStatus.SENT));

        OffsetDateTime capturedCutoff = cutoffCaptor.getValue();
        OffsetDateTime expectedCutoff = OffsetDateTime.now().minusDays(7);
        assertThat(capturedCutoff).isBetween(expectedCutoff.minusSeconds(5), expectedCutoff.plusSeconds(5));
    }

    @Test
    @DisplayName("Should complete without error when no records to delete")
    void should_complete_without_error_when_no_records_to_delete() {
        // Given
        when(outboxRepository.deleteSentRecordsOlderThan(any(), eq(MessageOutboxStatus.SENT))).thenReturn(0);

        // When / Then: no exception thrown
        cleanupService.cleanup();
        verify(outboxRepository).deleteSentRecordsOlderThan(any(), eq(MessageOutboxStatus.SENT));
    }
}
