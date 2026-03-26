package com.pidabrow.starter.testing.outbox;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Generic assertions for {@code message_outbox} rows mapped as {@code Map} columns
 * (e.g. from native queries). No service-specific message types.
 */
public final class OutboxTestAssertions {

    private OutboxTestAssertions() {
    }

    public static void assertAllRowsHaveStatus(Iterable<Map<String, Object>> rows, String expectedStatus) {
        for (Map<String, Object> row : rows) {
            assertThat(row.get("status")).isEqualTo(expectedStatus);
        }
    }

    public static void assertAllRowsHaveTenantId(Iterable<Map<String, Object>> rows, UUID tenantId) {
        String expected = tenantId.toString();
        for (Map<String, Object> row : rows) {
            assertThat(row.get("tenant_id").toString()).isEqualTo(expected);
        }
    }
}
