package com.pidabrow.starter.testing.assertions;

import jakarta.persistence.EntityManager;
import org.hibernate.Filter;
import org.hibernate.Session;

import java.util.UUID;

/**
 * Helpers for tenant isolation checks using the Hibernate {@code tenantFilter}.
 */
public final class TenantIsolationAssertions {

    private TenantIsolationAssertions() {
    }

    public static void enableTenantFilter(EntityManager entityManager, UUID tenantId) {
        Session session = entityManager.unwrap(Session.class);
        Filter filter = session.enableFilter("tenantFilter");
        filter.setParameter("tenantId", tenantId);
    }
}
