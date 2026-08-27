package com.firefly.auth.service;

import com.firefly.auth.domain.UserAccount;
import com.firefly.auth.domain.UserRole;
import com.firefly.auth.repository.UserAccountRepository;
import com.firefly.auth.repository.AuthAuditRepository;
import com.firefly.auth.repository.RevokedTokenRepository;
import com.firefly.auth.domain.AuthAuditEvent;
import com.firefly.auth.domain.RevokedToken;
import com.firefly.common.security.JwtService;
import com.firefly.common.security.TokenClaims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class AuthService {
    private final UserAccountRepository repository;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;
    private final RevokedTokenRepository revokedTokens;
    private final AuthAuditRepository audit;
    private final int maxFailures;
    private final long lockMinutes;
    private final long expiresInSeconds;
    private final String dummyPasswordHash;

    public AuthService(
            UserAccountRepository repository,
            PasswordEncoder encoder,
            JwtService jwtService,
            RevokedTokenRepository revokedTokens,
            AuthAuditRepository audit,
            @Value("${firefly.login.max-failures:5}") int maxFailures,
            @Value("${firefly.login.lock-minutes:15}") long lockMinutes,
            @Value("${firefly.jwt.ttl-hours:8}") long ttlHours) {
        if (maxFailures < 1 || lockMinutes < 1 || ttlHours < 1) {
            throw new IllegalArgumentException("Login protection and JWT TTL values must be positive");
        }
        this.repository = repository;
        this.encoder = encoder;
        this.jwtService = jwtService;
        this.revokedTokens = revokedTokens;
        this.audit = audit;
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
            audit.save(new AuthAuditEvent("LOGIN", normalizedUsername, false, "用户名或密码错误"));
            throw LoginException.invalidCredentials();
        }

        LocalDateTime now = LocalDateTime.now();
        revokedTokens.deleteByExpiresAtBefore(now);
        if (!user.isEnabled()) {
            audit.save(new AuthAuditEvent("LOGIN", normalizedUsername, false, "账号未启用"));
            throw LoginException.invalidCredentials();
        }
        if (user.isLocked(now)) {
            audit.save(new AuthAuditEvent("LOGIN", normalizedUsername, false, "账号已锁定"));
            throw LoginException.locked();
        }
        if (user.getLockedUntil() != null) {
            user.loginSucceeded();
        }

        if (!encoder.matches(password == null ? "" : password, user.getPasswordHash())) {
            user.loginFailed(maxFailures, now.plusMinutes(lockMinutes));
            repository.saveAndFlush(user);
            audit.save(new AuthAuditEvent("LOGIN", normalizedUsername, false, "用户名或密码错误"));
            if (user.isLocked(now)) throw LoginException.locked();
            throw LoginException.invalidCredentials();
        }

        if (user.getFailedLoginAttempts() > 0 || user.getLockedUntil() != null) {
            user.loginSucceeded();
            repository.save(user);
        }
        String role = UserRole.parse(user.getRole()).code();
        String token = jwtService.create(new TokenClaims(user.getId(), user.getUsername(), role));
        audit.save(new AuthAuditEvent("LOGIN", normalizedUsername, true, "登录成功"));
        return new LoginResult(token, "Bearer", expiresInSeconds, toUserInfo(user));
    }

    @Transactional
    public void logout(String token) {
        TokenClaims claims = jwtService.parse(token);
        revokedTokens.deleteByExpiresAtBefore(LocalDateTime.now());
        revokedTokens.save(new RevokedToken(claims.tokenId(), claims.userId(),
                LocalDateTime.ofInstant(claims.expiresAt(), ZoneId.systemDefault())));
        audit.save(new AuthAuditEvent("LOGOUT", claims.username(), true, "主动退出登录"));
    }

    @Transactional(readOnly = true)
    public boolean tokenActive(Long userId, String role, String tokenId) {
        if (userId == null || role == null || tokenId == null || revokedTokens.existsById(tokenId)) return false;
        return repository.findById(userId).filter(UserAccount::isEnabled)
                .filter(user -> !user.isLocked(LocalDateTime.now()))
                .map(user -> UserRole.parse(user.getRole()).code().equals(UserRole.parse(role).code()))
                .orElse(false);
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
