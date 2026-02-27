package com.pidabrow.starter.sample.infrastructure.persistence.adapter;

import com.pidabrow.starter.sample.application.port.out.DeleteNotificationRequestPort;
import com.pidabrow.starter.sample.application.port.out.SaveNotificationRequestPort;
import com.pidabrow.starter.sample.domain.user.NotificationRequest;
import com.pidabrow.starter.sample.infrastructure.persistence.entity.NotificationRequestEntity;
import com.pidabrow.starter.sample.infrastructure.persistence.repository.NotificationRequestEntityRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Outbound adapter for notification request persistence.
 * This is a package-private implementation detail.
 */
@Component
class NotificationRequestPersistenceAdapter implements SaveNotificationRequestPort, DeleteNotificationRequestPort {

    private final NotificationRequestEntityRepository repository;

    NotificationRequestPersistenceAdapter(NotificationRequestEntityRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public NotificationRequest save(NotificationRequest notificationRequest) {
        NotificationRequestEntity entity = NotificationRequestEntity.fromDomain(notificationRequest);
        NotificationRequestEntity saved = repository.save(entity);
        return saved.toDomain();
    }

    @Override
    @Transactional
    public void deleteByUserId(UUID userId) {
        repository.deleteByUserId(userId);
    }
}

