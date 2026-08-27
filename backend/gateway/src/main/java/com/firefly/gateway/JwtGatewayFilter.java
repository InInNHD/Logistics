package com.firefly.gateway;

import com.firefly.common.security.JwtService;
import com.firefly.common.security.TokenClaims;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

@Component
class JwtGatewayFilter implements GlobalFilter, Ordered {
    private static final Set<String> IDENTITY_HEADERS = Set.of("X-User-Id", "X-Username", "X-User-Role");
    private static final Set<String> OPERATOR_ROLES = Set.of("WAREHOUSE_MANAGER", "RECEIVER", "PICKER");
    private final JwtService jwtService;
    JwtGatewayFilter(JwtService jwtService) { this.jwtService = jwtService; }

    @Override public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        exchange = exchange.mutate().request(request -> request.headers(headers ->
                IDENTITY_HEADERS.forEach(headers::remove))).build();
        String path = exchange.getRequest().getURI().getPath();
        if (HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod()) || path.equals("/api/auth/login")
                || path.equals("/actuator/health") || path.startsWith("/actuator/health/") || path.equals("/actuator/info")) {
            return chain.filter(exchange);
        }
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return unauthorized(exchange, "请先登录");
        }
        try {
            TokenClaims claims = jwtService.parse(authorization.substring(7));
            String role = normalizeRole(claims.role());
            if (!isAuthorized(exchange.getRequest().getMethod(), path, role)) {
                return forbidden(exchange, "无权执行该操作");
            }
            ServerWebExchange authenticated = exchange.mutate().request(request -> request.headers(headers -> {
                headers.set("X-User-Id", claims.userId().toString());
                headers.set("X-Username", claims.username());
                headers.set("X-User-Role", role);
            })).build();
            return chain.filter(authenticated);
        } catch (Exception ignored) { return unauthorized(exchange, "登录凭证无效或已过期"); }
    }

    private boolean isAuthorized(HttpMethod method, String path, String role) {
        if ("ADMIN".equals(role)) return true;
        if (!OPERATOR_ROLES.contains(role)) return false;
        if (path.startsWith("/actuator/")) return false;
        if (path.startsWith("/api/auth/users") || path.equals("/api/auth/roles")) return false;
        if (HttpMethod.GET.equals(method)) return true;
        if ("WAREHOUSE_MANAGER".equals(role)) return path.startsWith("/api/") && !path.startsWith("/api/auth/");
        if ("RECEIVER".equals(role) && HttpMethod.POST.equals(method)) {
            return path.equals("/api/inbound-orders") || path.matches("/api/inbound-orders/\\d+/receive");
        }
        if ("PICKER".equals(role) && HttpMethod.POST.equals(method)) {
            return path.equals("/api/outbound-orders")
                    || path.matches("/api/outbound-orders/\\d+/(allocate|ship)");
        }
        return false;
    }

    private String normalizeRole(String role) {
        if (role == null) return "";
        String normalized = role.toUpperCase(Locale.ROOT);
        return "WAREHOUSE_ADMIN".equals(normalized) ? "ADMIN" : normalized;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes = ("{\"code\":401,\"message\":\"" + message + "\",\"data\":null}").getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private Mono<Void> forbidden(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes = ("{\"code\":403,\"message\":\"" + message + "\",\"data\":null}").getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    }

    @Override public int getOrder() { return -100; }
}
