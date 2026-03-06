package com.pidabrow.starter.sample.application.usecase;

import com.pidabrow.starter.common.tenant.TenantContextHolder;
import com.pidabrow.starter.sample.application.port.out.FindUserPort;
import com.pidabrow.starter.sample.domain.user.User;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Use case for finding users within the current tenant context.
 * This is an inbound port implementation following hexagonal architecture.
 */
@Component
public class FindUsersUseCase {

    private final FindUserPort findUserPort;

    public FindUsersUseCase(FindUserPort findUserPort) {
        this.findUserPort = findUserPort;
    }

    /**
     * Lists all users for the current tenant.
     *
     * @return list of users belonging to the current tenant
     */
    @Transactional(readOnly = true)
    public List<User> findAll() {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context must be set");
        }

        return findUserPort.findAll();
    }

    /**
     * Finds a single user by ID within the current tenant context.
     *
     * @param userId the user ID
     * @return the user
     * @throws NoSuchElementException if user not found or belongs to another tenant
     */
    @Transactional(readOnly = true)
    public User findById(UUID userId) {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context must be set");
        }

        return findUserPort.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));
    }
}

