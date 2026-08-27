package com.firefly.common.security;

import java.time.Instant;

public record TokenClaims(Long userId, String username, String role, String tokenId, Instant expiresAt) {
    public TokenClaims(Long userId, String username, String role) {
        this(userId, username, role, null, null);
    }
}
