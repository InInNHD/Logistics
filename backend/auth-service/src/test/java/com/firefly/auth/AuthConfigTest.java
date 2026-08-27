package com.firefly.auth;

import com.firefly.auth.config.AuthConfig;
import com.firefly.common.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthConfigTest {
    @Test void productionRejectsTheDocumentedDevelopmentSecret() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("production");
        assertThrows(IllegalStateException.class, () -> new AuthConfig().jwtService(
                "change-me-to-at-least-32-random-characters",
                8,
                JwtService.DEFAULT_ISSUER,
                JwtService.DEFAULT_AUDIENCE,
                environment));
    }
}
