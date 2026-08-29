package com.firefly.warehouse;

import com.firefly.common.security.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;

@Configuration
class WarehouseSecurityConfig {
    private static final Set<String> INSECURE_SECRETS = Set.of(
            "firefly-logistics-change-this-jwt-secret-in-production",
            "change-me-to-at-least-32-random-characters");

    @Bean
    JwtService warehouseJwtService(
            @Value("${firefly.jwt.secret:firefly-logistics-change-this-jwt-secret-in-production}") String secret,
            @Value("${firefly.jwt.issuer:firefly-logistics}") String issuer,
            @Value("${firefly.jwt.audience:firefly-logistics-web}") String audience,
            Environment environment) {
        if (isProduction(environment) && Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> profile.equalsIgnoreCase("demo"))) {
            throw new IllegalStateException("Demo profile cannot be enabled in production");
        }
        if (isProduction(environment) && INSECURE_SECRETS.contains(secret)) {
            throw new IllegalStateException("Production profile requires a unique JWT_SECRET");
        }
        return new JwtService(secret, Duration.ofHours(8), issuer, audience);
    }

    @Bean
    SecurityFilterChain warehouseSecurityFilterChain(
            HttpSecurity http,
            WarehouseJwtAuthenticationFilter jwtFilter,
            @Value("${firefly.security.enabled:true}") boolean securityEnabled,
            Environment environment) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable());

        if (!securityEnabled) {
            if (isProduction(environment)) {
                throw new IllegalStateException("Warehouse security cannot be disabled in production");
            }
            return http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
        }

        return http
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                writeError(response, 401, "请先登录"))
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(response, 403, "无权执行该操作")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/carrier-accounts", "/api/carrier-sync-logs")
                                .hasAnyRole("ADMIN", "WAREHOUSE_MANAGER")
                        .requestMatchers(HttpMethod.GET, "/api/**")
                                .hasAnyRole("ADMIN", "WAREHOUSE_MANAGER", "RECEIVER", "PICKER")
                        .requestMatchers(HttpMethod.POST,
                                "/api/warehouses", "/api/locations", "/api/products", "/api/partners",
                                "/api/inventory/adjustments", "/api/inventory/transfers", "/api/inventory/stocktakes",
                                "/api/carrier-accounts", "/api/carrier-accounts/*/test", "/api/carrier-accounts/*/sync")
                                .hasAnyRole("ADMIN", "WAREHOUSE_MANAGER")
                        .requestMatchers(HttpMethod.PUT, "/api/carrier-accounts/*")
                                .hasAnyRole("ADMIN", "WAREHOUSE_MANAGER")
                        .requestMatchers(HttpMethod.POST,
                                "/api/inbound-orders", "/api/inbound-orders/*/receive")
                                .hasAnyRole("ADMIN", "WAREHOUSE_MANAGER", "RECEIVER")
                        .requestMatchers(HttpMethod.POST,
                                "/api/outbound-orders", "/api/outbound-orders/*/allocate",
                                "/api/outbound-orders/*/pick", "/api/outbound-orders/*/pack",
                                "/api/outbound-orders/*/ship", "/api/outbound-orders/*/cancel",
                                "/api/outbound-orders/*/return")
                                .hasAnyRole("ADMIN", "WAREHOUSE_MANAGER", "PICKER")
                        .anyRequest().denyAll())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    private static boolean isProduction(Environment environment) {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> profile.equalsIgnoreCase("prod") || profile.equalsIgnoreCase("production"));
    }

    private static void writeError(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(code);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":" + code + ",\"message\":\"" + message + "\",\"data\":null}");
    }
}
