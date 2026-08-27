package com.firefly.auth.service;

import com.firefly.auth.domain.UserAccount;
import com.firefly.auth.domain.UserRole;
import com.firefly.auth.repository.UserAccountRepository;
import com.firefly.common.security.JwtService;
import com.firefly.common.security.TokenClaims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuthService {
    private final UserAccountRepository repository;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;
    private final int maxFailures;
    private final long lockMinutes;
    private final long expiresInSeconds;
    private final String dummyPasswordHash;

    public AuthService(
            UserAccountRepository repository,
            PasswordEncoder encoder,
            JwtService jwtService,
            @Value("${firefly.login.max-failures:5}") int maxFailures,
            @Value("${firefly.login.lock-minutes:15}") long lockMinutes,
            @Value("${firefly.jwt.ttl-hours:8}") long ttlHours) {
        if (maxFailures < 1 || lockMinutes < 1 || ttlHours < 1) {
            throw new IllegalArgumentException("Login protection and JWT TTL values must be positive");
        }
        this.repository = repository;
        this.encoder = encoder;
        this.jwtService = jwtService;
        this.maxFailures = maxFailures;
        this.lockMinutes = lockMinutes;
        this.expiresInSeconds = ttlHours * 3600;
        this.dummyPasswordHash = encoder.encode("firefly-dummy-password");
    }

    @Transactional(noRollbackFor = LoginException.class)
    public LoginResult login(String username, String password) {
        String normalizedUsername = username == null ? "" : username.trim();
        UserAccount user = repository.lockByUsername(normalizedUsername).orElse(null);
        if (user == null) {
            encoder.matches(password == null ? "" : password, dummyPasswordHash);
            throw LoginException.invalidCredentials();
        }

        LocalDateTime now = LocalDateTime.now();
        if (!user.isEnabled()) throw LoginException.invalidCredentials();
        if (user.isLocked(now)) throw LoginException.locked();
        if (user.getLockedUntil() != null) {
            user.loginSucceeded();
        }

        if (!encoder.matches(password == null ? "" : password, user.getPasswordHash())) {
            user.loginFailed(maxFailures, now.plusMinutes(lockMinutes));
            repository.saveAndFlush(user);
            if (user.isLocked(now)) throw LoginException.locked();
            throw LoginException.invalidCredentials();
        }

        if (user.getFailedLoginAttempts() > 0 || user.getLockedUntil() != null) {
            user.loginSucceeded();
            repository.save(user);
        }
        String role = UserRole.parse(user.getRole()).code();
        String token = jwtService.create(new TokenClaims(user.getId(), user.getUsername(), role));
        return new LoginResult(token, "Bearer", expiresInSeconds, toUserInfo(user));
    }

    @Transactional(readOnly = true)
    public UserInfo currentUser(Long id) {
        UserAccount user = repository.findById(id)
                .filter(UserAccount::isEnabled)
                .orElseThrow(LoginException::invalidCredentials);
        return toUserInfo(user);
    }

    private UserInfo toUserInfo(UserAccount user) {
        String role = UserRole.parse(user.getRole()).code();
        return new UserInfo(user.getId(), user.getUsername(), user.getDisplayName(), role, List.of(role));
    }

    public record LoginResult(String accessToken, String tokenType, long expiresIn, UserInfo user) {}
    public record UserInfo(Long id, String username, String displayName, String role, List<String> roles) {}

    public static final class LoginException extends RuntimeException {
        private final int code;
        private LoginException(int code, String message) { super(message); this.code = code; }
        public int code() { return code; }
        public static LoginException invalidCredentials() { return new LoginException(401, "用户名或密码错误"); }
        public static LoginException locked() { return new LoginException(429, "登录失败次数过多，请稍后再试"); }
    }
}
