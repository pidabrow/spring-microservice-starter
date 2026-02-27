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

import java.util.UUID;

/**
 * Use case for updating a user.
 * This is an inbound port implementation following hexagonal architecture.
 * 
 * Event: Publishes UserUpdatedEvent with JSON Patch delta after successful commit.
 */
@Component
public class UpdateUserUseCase {

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
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // Verify tenant isolation
        if (!existingUser.tenantId().equals(tenantId)) {
            throw new IllegalStateException("User does not belong to current tenant");
        }

        // Create updated user (immutable domain model)
        User updatedUser = new User(
                existingUser.id(),
                existingUser.tenantId(),
                email != null ? email : existingUser.email(),
                phoneNumber != null ? phoneNumber : existingUser.phoneNumber(),
                firstName != null ? firstName : existingUser.firstName(),
                lastName != null ? lastName : existingUser.lastName(),
                preferences != null ? preferences : existingUser.preferences()
        );

        // Generate JSON Patch delta
        String delta = JsonPatchGenerator.generatePatch(existingUser, updatedUser);

        // Save updated user
        User savedUser = saveUserPort.save(updatedUser);

        // Publish domain event (will be handled AFTER_COMMIT)
        UserUpdatedEvent event = UserUpdatedEvent.of(savedUser.id(), tenantId, delta);
        eventPublisher.publish(event);

        return savedUser;
    }
}

