package com.pidabrow.starter.sample.integration;

import com.pidabrow.starter.common.actor.ActorContextHolder;
import com.pidabrow.starter.common.actor.SystemActor;
import com.pidabrow.starter.common.tenant.TenantContext;
import com.pidabrow.starter.common.tenant.TenantContextHolder;
import com.pidabrow.starter.sample.MicroserviceStarterApplication;
import com.pidabrow.starter.testing.AbstractIntegrationTest;
import com.pidabrow.starter.testing.outbox.OutboxTestAssertions;
import com.pidabrow.starter.testing.tenant.TenantTestFixtures;
import com.pidabrow.starter.sample.application.usecase.CreateUserUseCase;
import com.pidabrow.starter.sample.domain.user.User;
import com.pidabrow.starter.sample.domain.user.UserPreferences;
import jakarta.persistence.EntityManager;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test for the Transactional Outbox mechanism (ADR-007).
 * <p>
 * Verifies the full flow:
 * <ol>
 *   <li>Trigger Use Case → PENDING record exists in DB</li>
 *   <li>Wait for Scheduler → message appears on Kafka with correct headers</li>
 *   <li>Verify DB status is updated to SENT</li>
 * </ol>
 * <p>
 * Uses Testcontainers (PostgreSQL + Kafka).
 */
@SpringBootTest(
        classes = MicroserviceStarterApplication.class,
        properties = {
                "outbox.enabled=true",
                "outbox.relay.poll-interval-ms=1000"
        }
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OutboxIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CreateUserUseCase createUserUseCase;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            entityManager.createNativeQuery("DELETE FROM message_outbox").executeUpdate();
            entityManager.flush();
            entityManager.clear();
            return null;
        });

        tenantId = TenantTestFixtures.persistTenant(entityManager, transactionManager, "Outbox Test Tenant");
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clearContext();
        ActorContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should persist PENDING outbox records within the same transaction as the use case")
    void should_persist_pending_outbox_records_within_same_transaction() {
        // Given
        TenantContextHolder.setContext(TenantContext.of(tenantId));
        ActorContextHolder.setContext(SystemActor.instance());

        // When: execute use case in a transaction
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        User user = tx.execute(status -> createUserUseCase.execute(
                "outbox@example.com", "+1234567890", "Outbox", "Test",
                new UserPreferences(true, false)
        ));

        // Then: outbox records exist with PENDING status
        List<Map<String, Object>> outboxRecords = queryOutboxRecords();
        assertThat(outboxRecords).isNotEmpty();

        // Should have at least WELCOME_EMAIL_REQUEST (from UserCreatedEvent) and NOTIFICATION_REQUESTED
        List<String> messageTypes = outboxRecords.stream()
                .map(r -> (String) r.get("message_type"))
                .toList();
        assertThat(messageTypes).contains("WELCOME_EMAIL_REQUEST", "NOTIFICATION_REQUESTED");

        OutboxTestAssertions.assertAllRowsHaveStatus(outboxRecords, "PENDING");
        OutboxTestAssertions.assertAllRowsHaveTenantId(outboxRecords, tenantId);
    }

    @Test
    @DisplayName("Should relay outbox messages to Kafka and update status to SENT")
    void should_relay_outbox_messages_to_kafka_and_update_status_to_sent() {
        // Given: create user (produces outbox records)
        TenantContextHolder.setContext(TenantContext.of(tenantId));
        ActorContextHolder.setContext(SystemActor.instance());

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        User user = tx.execute(status -> createUserUseCase.execute(
                "kafka@example.com", "+9876543210", "Kafka", "Test",
                new UserPreferences(true, true)
        ));

        // When: wait for the relay to process records
        // Then: DB records should eventually be SENT
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    List<Map<String, Object>> records = queryOutboxRecords();
                    assertThat(records).isNotEmpty();
                    assertThat(records).allSatisfy(record ->
                            assertThat(record.get("status")).isEqualTo("SENT"));
                });
    }

    @Test
    @DisplayName("Should publish Kafka messages with correct headers")
    void should_publish_kafka_messages_with_correct_headers() {
        // Given: create a Kafka consumer
        try (KafkaConsumer<String, String> consumer = createConsumer()) {
            consumer.subscribe(List.of("domain-events", "notification-events"));

            // Create user
            TenantContextHolder.setContext(TenantContext.of(tenantId));
            ActorContextHolder.setContext(SystemActor.instance());

            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.execute(status -> createUserUseCase.execute(
                    "headers@example.com", "+5555555555", "Headers", "Test",
                    new UserPreferences(true, false)
            ));

            // When: wait for messages on Kafka
            List<ConsumerRecord<String, String>> receivedRecords = new ArrayList<>();
            await().atMost(30, TimeUnit.SECONDS)
                    .pollInterval(Duration.ofSeconds(1))
                    .until(() -> {
                        ConsumerRecords<String, String> polled = consumer.poll(Duration.ofMillis(500));
                        polled.forEach(receivedRecords::add);
                        return receivedRecords.stream()
                                .filter(record -> {
                                    Header tenantHeader = record.headers().lastHeader("x-tenant-id");
                                    return tenantHeader != null
                                            && new String(tenantHeader.value(), StandardCharsets.UTF_8).equals(tenantId.toString());
                                })
                                .count() >= 2; // WELCOME_EMAIL_REQUEST + NOTIFICATION_REQUESTED
                    });

            // Then: messages have correct headers
            List<ConsumerRecord<String, String>> tenantRecords = receivedRecords.stream()
                    .filter(record -> {
                        Header tenantHeader = record.headers().lastHeader("x-tenant-id");
                        return tenantHeader != null
                                && new String(tenantHeader.value(), StandardCharsets.UTF_8).equals(tenantId.toString());
                    })
                    .toList();
            for (ConsumerRecord<String, String> record : tenantRecords) {
                Header tenantHeader = record.headers().lastHeader("x-tenant-id");
                assertThat(tenantHeader).isNotNull();
                assertThat(new String(tenantHeader.value(), StandardCharsets.UTF_8))
                        .isEqualTo(tenantId.toString());

                Header messageTypeHeader = record.headers().lastHeader("x-message-type");
                assertThat(messageTypeHeader).isNotNull();

                Header correlationHeader = record.headers().lastHeader("x-correlation-id");
                assertThat(correlationHeader).isNotNull();
            }

            // Verify partition key is the entity ID
            tenantRecords.forEach(record ->
                    assertThat(record.key()).isNotNull().isNotBlank());
        }
    }

    // --- Helpers ---

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> queryOutboxRecords() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(status -> {
            List<Object[]> results = entityManager.createNativeQuery(
                    "SELECT id, tenant_id, message_type, status, partition_key FROM message_outbox ORDER BY created_at"
            ).getResultList();

            return results.stream().map(row -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", row[0]);
                map.put("tenant_id", row[1]);
                map.put("message_type", row[2]);
                map.put("status", row[3]);
                map.put("partition_key", row[4]);
                return map;
            }).toList();
        });
    }

    private KafkaConsumer<String, String> createConsumer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "outbox-test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(props);
    }
}

