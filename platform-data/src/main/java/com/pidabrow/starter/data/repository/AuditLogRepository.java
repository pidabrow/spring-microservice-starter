package com.pidabrow.starter.data.repository;

import com.pidabrow.starter.data.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for AuditLog entity.
 * This is an outbound adapter for persistence.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
}

