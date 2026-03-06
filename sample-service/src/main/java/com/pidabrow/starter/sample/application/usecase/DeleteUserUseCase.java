package com.pidabrow.starter.sample.application.usecase;

import com.pidabrow.starter.common.event.DomainEventPublisher;
import com.pidabrow.starter.common.event.UserDeletedEvent;
import com.pidabrow.starter.common.tenant.TenantContextHolder;
import com.pidabrow.starter.sample.application.port.out.DeleteNotificationRequestPort;
import com.pidabrow.starter.sample.application.port.out.DeleteUserPort;
import com.pidabrow.starter.sample.application.port.out.FindUserPort;
import com.pidabrow.starter.sample.domain.user.User;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Use case for deleting a user.
 * This is an inbound port implementation following hexagonal architecture.
 * 
 * Event: Publishes UserDeletedEvent after successful commit.
 */
@Component
public class DeleteUserUseCase {

    private final FindUserPort findUserPort;
    private final DeleteUserPort deleteUserPort;
    private final DeleteNotificationRequestPort deleteNotificationRequestPort;
    private final DomainEventPublisher eventPublisher;

    public DeleteUserUseCase(
            FindUserPort findUserPort,
            DeleteUserPort deleteUserPort,
            DeleteNotificationRequestPort deleteNotificationRequestPort,
            DomainEventPublisher eventPublisher) {
        this.findUserPort = findUserPort;
        this.deleteUserPort = deleteUserPort;
        this.deleteNotificationRequestPort = deleteNotificationRequestPort;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void execute(UUID userId) {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context must be set");
        }

        // Find existing user to verify it exists and belongs to tenant
        User existingUser = findUserPort.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));

        // Verify tenant isolation (defense-in-depth; Hibernate filter normally prevents cross-tenant access)
        if (!existingUser.tenantId().equals(tenantId)) {
            throw new NoSuchElementException("User not found: " + userId);
        }

        // Delete notification requests first (to avoid foreign key constraint violation)
        deleteNotificationRequestPort.deleteByUserId(userId);

        // Delete user
        deleteUserPort.deleteById(userId);

        // Publish domain event (will be handled AFTER_COMMIT)
        UserDeletedEvent event = UserDeletedEvent.of(userId, tenantId);
        eventPublisher.publish(event);
    }
}

