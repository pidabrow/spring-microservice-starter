package com.pidabrow.starter.sample.infrastructure.persistence.adapter;

import com.pidabrow.starter.sample.application.port.out.SaveUserPort;
import com.pidabrow.starter.sample.domain.user.User;
import com.pidabrow.starter.sample.infrastructure.persistence.entity.UserEntity;
import com.pidabrow.starter.sample.infrastructure.persistence.repository.UserEntityRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbound adapter for user persistence.
 * This is a package-private implementation detail.
 */
@Component
class UserPersistenceAdapter implements SaveUserPort {

    private final UserEntityRepository repository;

    UserPersistenceAdapter(UserEntityRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public User save(User user) {
        UserEntity entity = UserEntity.fromDomain(user);
        UserEntity saved = repository.save(entity);
        return saved.toDomain();
    }
}

