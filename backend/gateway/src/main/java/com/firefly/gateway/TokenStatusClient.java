package com.firefly.gateway;

import com.firefly.common.security.TokenClaims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
class TokenStatusClient {
    private final WebClient client;
    private final boolean enabled;

    TokenStatusClient(WebClient.Builder builder,
                      @Value("${firefly.auth-service-url:http://localhost:8081}") String authServiceUrl,
                      @Value("${firefly.token-status-check-enabled:true}") boolean enabled) {
        this.client = builder.baseUrl(authServiceUrl).build();
        this.enabled = enabled;
    }

    Mono<Boolean> active(TokenClaims claims) {
        if (!enabled) return Mono.just(true);
        return client.post().uri("/api/auth/token-status")
                .bodyValue(new StatusRequest(claims.userId(), claims.role(), claims.tokenId()))
                .retrieve().bodyToMono(StatusResponse.class)
                .map(response -> response.code == 0 && Boolean.TRUE.equals(response.data))
                .onErrorReturn(false);
    }

    private record StatusRequest(Long userId, String role, String tokenId) {}
    private record StatusResponse(int code, Boolean data) {}
}
