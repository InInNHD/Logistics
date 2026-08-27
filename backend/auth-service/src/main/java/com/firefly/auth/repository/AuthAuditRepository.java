package com.firefly.auth.repository;

import com.firefly.auth.domain.AuthAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthAuditRepository extends JpaRepository<AuthAuditEvent, Long> {}
