package com.firefly.auth.config;

import com.firefly.auth.domain.UserAccount;
import com.firefly.auth.domain.SecurityGuard;
import com.firefly.auth.repository.SecurityGuardRepository;
import com.firefly.auth.repository.UserAccountRepository;
import com.firefly.common.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.dao.DataIntegrityViolationException;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;

@Configuration
public class AuthConfig {
    private static final Set<String> INSECURE_SECRETS = Set.of(
            "firefly-logistics-change-this-jwt-secret-in-production",
            "change-me-to-at-least-32-random-characters");
    private static final String DEVELOPMENT_PASSWORD = "Firefly@123";

    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean public JwtService jwtService(
            @Value("${firefly.jwt.secret}") String secret,
            @Value("${firefly.jwt.ttl-hours:8}") long ttlHours,
            @Value("${firefly.jwt.issuer}") String issuer,
            @Value("${firefly.jwt.audience}") String audience,
            Environment environment) {
        if (isProduction(environment) && INSECURE_SECRETS.contains(secret)) {
            throw new IllegalStateException("Production profile requires a unique JWT_SECRET");
        }
        return new JwtService(secret, Duration.ofHours(ttlHours), issuer, audience);
    }

    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> writeError(response, 401, "请先登录"))
                        .accessDeniedHandler((request, response, exception) -> writeError(response, 403, "无权执行该操作")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/api/auth/users/**", "/api/auth/roles").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean ApplicationRunner defaultAdmin(UserAccountRepository repository, PasswordEncoder encoder,
            Environment environment,
            @Value("${firefly.admin.bootstrap-enabled:true}") boolean bootstrapEnabled,
            @Value("${firefly.admin.username:admin}") String username,
            @Value("${firefly.admin.password:Firefly@123}") String password) {
        return args -> {
            if (!bootstrapEnabled) return;
            if (isProduction(environment) && DEVELOPMENT_PASSWORD.equals(password)) {
                throw new IllegalStateException("Production profile requires a unique ADMIN_PASSWORD or ADMIN_BOOTSTRAP_ENABLED=false");
            }
            if (repository.existsByUsernameIgnoreCase(username)) return;
            try {
                repository.saveAndFlush(new UserAccount(username, encoder.encode(password), "系统管理员", "ADMIN"));
            } catch (DataIntegrityViolationException exception) {
                if (!repository.existsByUsernameIgnoreCase(username)) throw exception;
            }
        };
    }

    @Bean ApplicationRunner securityGuard(SecurityGuardRepository repository) {
        return args -> {
            if (repository.existsById(SecurityGuard.ADMIN_SET_ID)) return;
            try {
                repository.saveAndFlush(new SecurityGuard(SecurityGuard.ADMIN_SET_ID, "ADMIN_SET"));
            } catch (DataIntegrityViolationException exception) {
                if (!repository.existsById(SecurityGuard.ADMIN_SET_ID)) throw exception;
            }
        };
    }

    private static boolean isProduction(Environment environment) {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> profile.equalsIgnoreCase("prod") || profile.equalsIgnoreCase("production"));
    }

    private static void writeError(jakarta.servlet.http.HttpServletResponse response, int code, String message)
            throws java.io.IOException {
        response.setStatus(code);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":" + code + ",\"message\":\"" + message + "\",\"data\":null}");
    }
}
