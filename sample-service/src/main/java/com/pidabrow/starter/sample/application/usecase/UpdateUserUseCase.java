package com.pidabrow.starter.sample.application.usecase;

import com.pidabrow.starter.common.event.DomainEventPublisher;
import com.pidabrow.starter.common.event.UserUpdatedEvent;
import com.pidabrow.starter.common.tenant.TenantContextHolder;
import com.pidabrow.starter.sample.application.port.out.FindUserPort;
import com.pidabrow.starter.sample.application.port.out.SaveUserPort;
import com.pidabrow.starter.sample.application.util.JsonPatchGenerator;
import com.pidabrow.starter.sample.domain.user.User;
import com.pidabrow.starter.sample.domain.user.UserPreferences;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Use case for updating a user.
 * This is an inbound port implementation following hexagonal architecture.
 * 
 * Event: Publishes UserUpdatedEvent with JSON Patch delta after successful commit.
 */
@Component
public class UpdateUserUseCase {

    private static final String EMPTY_JSON_PATCH = "[]";

    private final FindUserPort findUserPort;
    private final SaveUserPort saveUserPort;
    private final DomainEventPublisher eventPublisher;

    public UpdateUserUseCase(
            FindUserPort findUserPort,
            SaveUserPort saveUserPort,
            DomainEventPublisher eventPublisher) {
        this.findUserPort = findUserPort;
        this.saveUserPort = saveUserPort;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public User execute(
            UUID userId,
            String email,
            String phoneNumber,
            String firstName,
            String lastName,
            UserPreferences preferences) {
        
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context must be set");
        }

        // Find existing user
        User existingUser = findUserPort.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));

        // Verify tenant isolation (defense-in-depth; Hibernate filter normally prevents cross-tenant access)
        if (!existingUser.tenantId().equals(tenantId)) {
            throw new NoSuchElementException("User not found: " + userId);
        }

        // Create updated user (immutable domain model)
        User updatedUser = new User(
                existingUser.id(),
                existingUser.tenantId(),
                email != null ? email : existingUser.email(),
                phoneNumber != null ? phoneNumber : existingUser.phoneNumber(),
                firstName != null ? firstName : existingUser.firstName(),
                lastName != null ? lastName : existingUser.lastName(),
                preferences != null ? preferences : existingUser.preferences(),
                existingUser.passwordHash() // Preserve password hash
        );

        // Generate JSON Patch delta
        String delta = JsonPatchGenerator.generatePatch(existingUser, updatedUser);

        // Save updated user
        User savedUser = saveUserPort.save(updatedUser);

        // Publish domain event only if there are actual changes (handled in-transaction by @EventListener to write Outbox record)
        if (!EMPTY_JSON_PATCH.equals(delta)) {
            UserUpdatedEvent event = UserUpdatedEvent.of(savedUser.id(), tenantId, delta);
            eventPublisher.publish(event);
        }

        return savedUser;
    }
}

