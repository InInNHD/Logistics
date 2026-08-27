package com.firefly.gateway;

import com.firefly.common.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;

@Configuration
class GatewayConfig {
    private static final Set<String> INSECURE_SECRETS = Set.of(
            "firefly-logistics-change-this-jwt-secret-in-production",
            "change-me-to-at-least-32-random-characters");

    @Bean JwtService jwtService(
            @Value("${firefly.jwt.secret}") String secret,
            @Value("${firefly.jwt.issuer}") String issuer,
            @Value("${firefly.jwt.audience}") String audience,
            Environment environment) {
        boolean production = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> profile.equalsIgnoreCase("prod") || profile.equalsIgnoreCase("production"));
        if (production && INSECURE_SECRETS.contains(secret)) {
            throw new IllegalStateException("Production profile requires a unique JWT_SECRET");
        }
        return new JwtService(secret, Duration.ofHours(8), issuer, audience);
    }
}
