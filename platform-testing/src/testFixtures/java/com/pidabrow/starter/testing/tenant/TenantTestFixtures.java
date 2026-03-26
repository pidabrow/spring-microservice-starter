package com.pidabrow.starter.testing.tenant;

import com.pidabrow.starter.data.entity.Tenant;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * Persists a {@link Tenant} in its own transaction and returns the generated {@code tenantId}.
 */
public final class TenantTestFixtures {

    private TenantTestFixtures() {
    }

    public static UUID persistTenant(
            EntityManager entityManager,
            PlatformTransactionManager transactionManager,
            String name
    ) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(status -> {
            Tenant tenant = Tenant.create(name);
            entityManager.persist(tenant);
            entityManager.flush();
            entityManager.clear();
            return tenant.getId();
        });
    }
}
