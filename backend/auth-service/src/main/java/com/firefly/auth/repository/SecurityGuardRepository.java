package com.firefly.auth.repository;

import com.firefly.auth.domain.SecurityGuard;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SecurityGuardRepository extends JpaRepository<SecurityGuard, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select guard from SecurityGuard guard where guard.id = :id")
    Optional<SecurityGuard> lockById(@Param("id") Long id);
}
