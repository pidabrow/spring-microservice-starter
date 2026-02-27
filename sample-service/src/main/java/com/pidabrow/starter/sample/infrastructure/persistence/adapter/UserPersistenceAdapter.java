package com.pidabrow.starter.sample.infrastructure.persistence.adapter;

import com.pidabrow.starter.sample.application.port.out.DeleteUserPort;
import com.pidabrow.starter.sample.application.port.out.FindUserPort;
import com.pidabrow.starter.sample.application.port.out.SaveUserPort;
import com.pidabrow.starter.sample.domain.user.User;
import com.pidabrow.starter.sample.infrastructure.persistence.entity.UserEntity;
import com.pidabrow.starter.sample.infrastructure.persistence.repository.UserEntityRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.hibernate.Filter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound adapter for user persistence.
 * This is a package-private implementation detail.
 */
@Component
class UserPersistenceAdapter implements SaveUserPort, FindUserPort, DeleteUserPort {

    private final UserEntityRepository repository;
    private final EntityManager entityManager;

    UserPersistenceAdapter(UserEntityRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public User save(User user) {
        // Check if user exists
        Optional<UserEntity> existing = repository.findById(user.id());
        
        if (existing.isPresent()) {
            // Update existing entity
            UserEntity entity = existing.get();
            updateEntityFromDomain(entity, user);
            UserEntity saved = repository.save(entity);
            return saved.toDomain();
        } else {
            // Create new entity
            UserEntity entity = UserEntity.fromDomain(user);
            UserEntity saved = repository.save(entity);
            return saved.toDomain();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(UUID userId) {
        enableTenantFilter();
        return repository.findById(userId)
                .map(UserEntity::toDomain);
    }

    @Override
    @Transactional
    public void deleteById(UUID userId) {
        enableTenantFilter();
        repository.deleteById(userId);
    }

    private void updateEntityFromDomain(UserEntity entity, User user) {
        // Update fields using package-private method
        // This maintains immutability principles while allowing JPA updates
        entity.updateFromDomain(user);
    }

    private void enableTenantFilter() {
        Session session = entityManager.unwrap(Session.class);
        Filter filter = session.enableFilter("tenantFilter");
        filter.setParameter("tenantId", com.pidabrow.starter.common.tenant.TenantContextHolder.getTenantId());
    }
}

