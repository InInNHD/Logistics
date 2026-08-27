package com.firefly.common.security;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {
    @Test void createsAndParsesToken() {
        JwtService service = new JwtService("firefly-logistics-test-secret-key-123456", Duration.ofMinutes(5));
        TokenClaims claims = service.parse(service.create(new TokenClaims(1L, "admin", "ADMIN")));
        assertEquals("admin", claims.username()); assertEquals("ADMIN", claims.role());
    }

    @Test void rejectsTokenForAnotherAudience() {
        String secret = "firefly-logistics-test-secret-key-123456";
        JwtService issuer = new JwtService(secret, Duration.ofMinutes(5), "firefly", "web");
        JwtService otherAudience = new JwtService(secret, Duration.ofMinutes(5), "firefly", "mobile");
        String token = issuer.create(new TokenClaims(1L, "admin", "ADMIN"));
        assertThrows(RuntimeException.class, () -> otherAudience.parse(token));
    }

    @Test void assignsAUniqueTokenIdToEachToken() {
        JwtService service = new JwtService("firefly-logistics-test-secret-key-123456", Duration.ofMinutes(5));
        TokenClaims claims = new TokenClaims(1L, "admin", "ADMIN");
        assertNotEquals(service.create(claims), service.create(claims));
    }

    @Test void alwaysUsesHs256EvenWithALongerSecret() {
        JwtService service = new JwtService("a-secure-random-secret-that-is-deliberately-longer-than-sixty-four-bytes-123456789",
                Duration.ofMinutes(5));
        String token = service.create(new TokenClaims(1L, "admin", "ADMIN"));
        String header = new String(Base64.getUrlDecoder().decode(token.substring(0, token.indexOf('.'))),
                StandardCharsets.UTF_8);
        assertTrue(header.contains("\"alg\":\"HS256\""));
    }
}
