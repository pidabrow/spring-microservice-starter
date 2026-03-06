package com.pidabrow.starter.infrastructure.outbox;

import com.pidabrow.starter.common.outbox.MessagePublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Kafka-based implementation of {@link MessagePublisher}.
 * <p>
 * Publishes messages to Kafka topics using {@link KafkaTemplate}.
 * Headers (x-tenant-id, x-message-type, x-correlation-id) are propagated
 * as Kafka record headers.
 * <p>
 * This is a package-private infrastructure adapter.
 */
@Component
class KafkaMessagePublisher implements MessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaMessagePublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    KafkaMessagePublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(String destination, String key, Map<String, Object> payload, Map<String, String> headers) {
        try {
            String serializedPayload = objectMapper.writeValueAsString(payload);

            ProducerRecord<String, String> record = new ProducerRecord<>(destination, null, key, serializedPayload);

            if (headers != null) {
                headers.forEach((headerKey, headerValue) ->
                        record.headers().add(new RecordHeader(headerKey,
                                headerValue.getBytes(StandardCharsets.UTF_8))));
            }

            kafkaTemplate.send(record).get();

            log.debug("Message published to Kafka: topic={}, key={}", destination, key);
        } catch (JsonProcessingException e) {
            throw new MessagePublishException("Failed to serialize payload for topic=" + destination, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MessagePublishException("Kafka send interrupted for topic=" + destination, e);
        } catch (Exception e) {
            throw new MessagePublishException("Failed to publish message to topic=" + destination, e);
        }
    }

    /**
     * Runtime exception for message publishing failures.
     * Used by the relay service to handle retries and error tracking.
     */
    static class MessagePublishException extends RuntimeException {
        MessagePublishException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

