package com.firefly.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

public final class JwtService {
    public static final String DEFAULT_ISSUER = "firefly-logistics";
    public static final String DEFAULT_AUDIENCE = "firefly-logistics-web";

    private final SecretKey key;
    private final Duration ttl;
    private final String issuer;
    private final String audience;

    public JwtService(String secret, Duration ttl) {
        this(secret, ttl, DEFAULT_ISSUER, DEFAULT_AUDIENCE);
    }

    public JwtService(String secret, Duration ttl, String issuer, String audience) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT secret must contain at least 32 bytes");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("JWT ttl must be positive");
        }
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("JWT issuer and audience must not be blank");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = ttl;
        this.issuer = issuer;
        this.audience = audience;
    }

    public String create(TokenClaims claims) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .audience().add(audience).and()
                .id(UUID.randomUUID().toString())
                .subject(claims.username())
                .claim("uid", claims.userId())
                .claim("role", claims.role())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public TokenClaims parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .requireAudience(audience)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (claims.getId() == null || claims.getId().isBlank()) {
            throw new IllegalArgumentException("JWT id is required");
        }
        return new TokenClaims(claims.get("uid", Long.class), claims.getSubject(), claims.get("role", String.class));
    }
}
