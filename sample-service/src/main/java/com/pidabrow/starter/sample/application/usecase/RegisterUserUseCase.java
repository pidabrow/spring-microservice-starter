package com.pidabrow.starter.sample.application.usecase;

import com.pidabrow.starter.common.correlation.CorrelationContextHolder;
import com.pidabrow.starter.common.event.DomainEventPublisher;
import com.pidabrow.starter.common.event.UserCreatedEvent;
import com.pidabrow.starter.common.security.PasswordEncoder;
import com.pidabrow.starter.common.tenant.TenantContextHolder;
import com.pidabrow.starter.sample.application.port.out.CheckUserExistsPort;
import com.pidabrow.starter.sample.application.port.out.SaveUserPort;
import com.pidabrow.starter.sample.domain.user.User;
import com.pidabrow.starter.sample.domain.user.UserAlreadyExistsException;
import com.pidabrow.starter.sample.domain.user.UserPreferences;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Use case for user registration with password.
 * This is an inbound port implementation following hexagonal architecture.
 * 
 * Atomic Action: Saves User with password hash in one transaction.
 * Event: Publishes UserCreatedEvent with correlation ID after successful commit.
 * 
 * Per ADR-008:
 * - Email is normalized (trimmed and lowercased)
 * - Password is hashed using BCrypt with cost factor 12
 * - Uniqueness is enforced via database constraint
 * - Correlation ID is propagated to the event
 */
@Component
public class RegisterUserUseCase {

    private final SaveUserPort saveUserPort;
    private final CheckUserExistsPort checkUserExistsPort;
    private final PasswordEncoder passwordEncoder;
    private final DomainEventPublisher eventPublisher;

    public RegisterUserUseCase(
            SaveUserPort saveUserPort,
            CheckUserExistsPort checkUserExistsPort,
            PasswordEncoder passwordEncoder,
            DomainEventPublisher eventPublisher) {
        this.saveUserPort = saveUserPort;
        this.checkUserExistsPort = checkUserExistsPort;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public User execute(
            String email,
            String password,
            String firstName,
            String lastName) {
        
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context must be set");
        }

        // 1. Sanitize: Trim and lowercase the email
        String normalizedEmail = email.trim().toLowerCase();
        
        // 2. Validate: Perform a tenant-aware uniqueness check (fail-fast)
        if (checkUserExistsPort.existsByEmail(normalizedEmail)) {
            throw new UserAlreadyExistsException(normalizedEmail);
        }

        // 3. Hash: Execute PasswordEncoder.encode(rawPassword)
        String passwordHash = passwordEncoder.encode(password);

        // 4. Create user domain object with password hash
        UserPreferences defaultPreferences = new UserPreferences(true, false);
        // Use a placeholder phone number for registration (can be updated later)
        String placeholderPhoneNumber = "+0000000000";
        User user = User.createWithPassword(
                tenantId,
                normalizedEmail,
                placeholderPhoneNumber,
                firstName,
                lastName,
                defaultPreferences,
                passwordHash
        );

        // 5. Persist: Save User entity to DB
        // If database constraint violation occurs (race condition), map to UserAlreadyExistsException
        User savedUser;
        try {
            savedUser = saveUserPort.save(user);
        } catch (DataIntegrityViolationException e) {
            // Database unique constraint violation - map to domain exception
            throw new UserAlreadyExistsException(normalizedEmail);
        }

        // 6. Emit: Publish UserCreatedEvent (carrying the current Correlation ID)
        UUID correlationId = CorrelationContextHolder.getCorrelationId();
        UserCreatedEvent userCreatedEvent = UserCreatedEvent.of(savedUser.id(), tenantId, correlationId);
        eventPublisher.publish(userCreatedEvent);

        return savedUser;
    }
}

