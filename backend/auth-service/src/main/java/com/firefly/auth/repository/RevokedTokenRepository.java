package com.firefly.auth.repository;

import com.firefly.auth.domain.RevokedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;

public interface RevokedTokenRepository extends JpaRepository<RevokedToken, String> {
    void deleteByExpiresAtBefore(LocalDateTime cutoff);
}
