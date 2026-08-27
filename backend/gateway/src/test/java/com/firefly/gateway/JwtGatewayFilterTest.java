package com.firefly.gateway;

import com.firefly.common.security.JwtService;
import com.firefly.common.security.TokenClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.server.ServerWebExchange;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtGatewayFilterTest {
    private JwtService jwtService;
    private JwtGatewayFilter filter;

    @BeforeEach void setUp() {
        jwtService = new JwtService("firefly-logistics-gateway-test-key-123456", Duration.ofMinutes(5));
        filter = new JwtGatewayFilter(jwtService);
    }

    @Test void removesForgedIdentityHeadersAndInjectsVerifiedClaims() {
        String token = token("receiver", "RECEIVER");
        var request = MockServerHttpRequest.post("/api/inbound-orders/12/receive")
                .header("Authorization", "Bearer " + token)
                .header("X-User-Id", "999")
                .header("X-Username", "attacker")
                .header("X-User-Role", "ADMIN")
                .build();
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(MockServerWebExchange.from(request), exchange -> {
            forwarded.set(exchange);
            return exchange.getResponse().setComplete();
        }).block();

        assertEquals("7", forwarded.get().getRequest().getHeaders().getFirst("X-User-Id"));
        assertEquals("receiver", forwarded.get().getRequest().getHeaders().getFirst("X-Username"));
        assertEquals("RECEIVER", forwarded.get().getRequest().getHeaders().getFirst("X-User-Role"));
    }

    @Test void receiverCannotAdjustInventory() {
        var request = MockServerHttpRequest.post("/api/inventory/adjustments")
                .header("Authorization", "Bearer " + token("receiver", "RECEIVER"))
                .build();
        var exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, next -> next.getResponse().setComplete()).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test void onlyAdminCanReadUsers() {
        var request = MockServerHttpRequest.get("/api/auth/users")
                .header("Authorization", "Bearer " + token("manager", "WAREHOUSE_MANAGER"))
                .build();
        var exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, next -> next.getResponse().setComplete()).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test void publicLoginAlsoDropsForgedIdentityHeaders() {
        var request = MockServerHttpRequest.post("/api/auth/login")
                .header("X-User-Role", "ADMIN")
                .build();
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(MockServerWebExchange.from(request), exchange -> {
            forwarded.set(exchange);
            return exchange.getResponse().setComplete();
        }).block();

        assertNull(forwarded.get().getRequest().getHeaders().getFirst("X-User-Role"));
    }

    @Test void productionRejectsTheDocumentedDevelopmentSecret() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        assertThrows(IllegalStateException.class, () -> new GatewayConfig().jwtService(
                "change-me-to-at-least-32-random-characters",
                JwtService.DEFAULT_ISSUER,
                JwtService.DEFAULT_AUDIENCE,
                environment));
    }

    @Test void nonPublicActuatorEndpointsRequireAdmin() {
        var request = MockServerHttpRequest.get("/actuator/metrics")
                .header("Authorization", "Bearer " + token("manager", "WAREHOUSE_MANAGER"))
                .build();
        var exchange = MockServerWebExchange.from(request);
        filter.filter(exchange, next -> next.getResponse().setComplete()).block();
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test void unknownSignedRoleHasNoReadAccess() {
        var request = MockServerHttpRequest.get("/api/inventory")
                .header("Authorization", "Bearer " + token("legacy", "UNKNOWN_ROLE"))
                .build();
        var exchange = MockServerWebExchange.from(request);
        filter.filter(exchange, next -> next.getResponse().setComplete()).block();
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    private String token(String username, String role) {
        return jwtService.create(new TokenClaims(7L, username, role));
    }
}
