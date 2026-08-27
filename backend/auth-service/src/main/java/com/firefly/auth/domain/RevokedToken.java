package com.firefly.auth.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sys_revoked_token")
public class RevokedToken {
    @Id @Column(name = "token_id", length = 36) private String tokenId;
    @Column(nullable = false) private Long userId;
    @Column(nullable = false) private LocalDateTime expiresAt;
    @Column(nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
    protected RevokedToken() {}
    public RevokedToken(String tokenId, Long userId, LocalDateTime expiresAt) {
        this.tokenId = tokenId; this.userId = userId; this.expiresAt = expiresAt;
    }
}
