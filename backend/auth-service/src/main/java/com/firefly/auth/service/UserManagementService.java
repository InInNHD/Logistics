package com.firefly.auth.service;

import com.firefly.auth.domain.UserAccount;
import com.firefly.auth.domain.UserRole;
import com.firefly.auth.domain.SecurityGuard;
import com.firefly.auth.repository.SecurityGuardRepository;
import com.firefly.auth.repository.UserAccountRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
public class UserManagementService {
    private static final int MAX_PAGE_SIZE = 100;

    private final UserAccountRepository repository;
    private final SecurityGuardRepository guardRepository;
    private final PasswordEncoder encoder;

    public UserManagementService(UserAccountRepository repository, SecurityGuardRepository guardRepository, PasswordEncoder encoder) {
        this.repository = repository;
        this.guardRepository = guardRepository;
        this.encoder = encoder;
    }

    @Transactional(readOnly = true)
    public PageResult<UserView> list(Long operatorId, String keyword, String status, String role, int page, int size) {
        requireActiveAdmin(operatorId, false);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        LocalDateTime now = LocalDateTime.now();
        Specification<UserAccount> specification = (root, query, builder) -> builder.conjunction();

        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("username")), pattern),
                    builder.like(builder.lower(root.get("displayName")), pattern)));
        }
        if (role != null && !role.isBlank()) {
            String roleCode;
            try {
                roleCode = UserRole.parse(role).code();
            } catch (IllegalArgumentException exception) {
                throw ManagementException.badRequest(exception.getMessage());
            }
            specification = specification.and((root, query, builder) -> "ADMIN".equals(roleCode)
                    ? root.get("role").in("ADMIN", "WAREHOUSE_ADMIN")
                    : builder.equal(root.get("role"), roleCode));
        }
        if (status != null && !status.isBlank()) {
            String normalized = status.trim().toUpperCase(Locale.ROOT);
            specification = switch (normalized) {
                case "ACTIVE", "ENABLED" -> specification.and((root, query, builder) -> builder.and(
                        builder.isTrue(root.get("enabled")),
                        builder.or(builder.isNull(root.get("lockedUntil")), builder.lessThanOrEqualTo(root.get("lockedUntil"), now))));
                case "DISABLED", "INACTIVE" -> specification.and((root, query, builder) -> builder.isFalse(root.get("enabled")));
                case "LOCKED" -> specification.and((root, query, builder) -> builder.and(
                        builder.isTrue(root.get("enabled")), builder.greaterThan(root.get("lockedUntil"), now)));
                default -> throw ManagementException.badRequest("不支持的用户状态：" + status);
            };
        }

        var result = repository.findAll(specification,
                PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new PageResult<>(result.getContent().stream().map(user -> toView(user, now)).toList(),
                result.getTotalElements(), safePage, safeSize);
    }

    @Transactional
    public UserView create(Long operatorId, String username, String password, String displayName,
                           String role, List<String> roles, String status) {
        lockAdminSet();
        requireActiveAdmin(operatorId, true);
        String normalizedUsername = username == null ? "" : username.trim();
        if (repository.existsByUsernameIgnoreCase(normalizedUsername)) {
            throw ManagementException.conflict("用户名已存在");
        }
        validatePassword(password);
        String roleCode = resolveRole(role, roles, true);
        boolean enabled = resolveEnabled(status, true);
        UserAccount user = new UserAccount(normalizedUsername, encoder.encode(password), displayName.trim(), roleCode);
        if (!enabled) user.updateProfile(null, null, false);
        try {
            return toView(repository.saveAndFlush(user), LocalDateTime.now());
        } catch (DataIntegrityViolationException exception) {
            throw ManagementException.conflict("用户名已存在");
        }
    }

    @Transactional
    public UserView update(Long id, Long operatorId, String displayName, String role, List<String> roles, String status, String password) {
        lockAdminSet();
        UserAccount operator = requireActiveAdmin(operatorId, true);
        UserAccount user = operatorId.equals(id) ? operator : repository.lockById(id)
                .orElseThrow(() -> ManagementException.notFound("用户不存在"));

        String nextRole = resolveRole(role, roles, false);
        Boolean nextEnabled = status == null || status.isBlank() ? null : resolveEnabled(status, false);
        if (displayName != null && displayName.isBlank()) {
            throw ManagementException.badRequest("显示名称不能为空");
        }
        if (operator.getId().equals(id) && (Boolean.FALSE.equals(nextEnabled) || (nextRole != null && !"ADMIN".equals(nextRole)))) {
            throw ManagementException.badRequest("不能停用自己或移除自己的管理员角色");
        }
        boolean removesEnabledAdmin = isAdmin(user.getRole()) && user.isEnabled()
                && (Boolean.FALSE.equals(nextEnabled) || (nextRole != null && !"ADMIN".equals(nextRole)));
        if (removesEnabledAdmin && repository.countByRoleInAndEnabledTrue(List.of("ADMIN", "WAREHOUSE_ADMIN")) <= 1) {
            throw ManagementException.badRequest("系统至少需要一名启用的管理员");
        }

        user.updateProfile(displayName == null ? null : displayName.trim(), nextRole, nextEnabled);
        if (password != null) {
            validatePassword(password);
            user.resetPassword(encoder.encode(password));
        }
        return toView(repository.save(user), LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<RoleView> roles(Long operatorId) {
        requireActiveAdmin(operatorId, false);
        return Arrays.stream(UserRole.values())
                .map(role -> new RoleView(role.code(), role.displayName(), role.scope()))
                .toList();
    }

    private String resolveRole(String role, List<String> roles, boolean required) {
        String value = role;
        if (roles != null && !roles.isEmpty()) {
            if (roles.size() != 1) throw ManagementException.badRequest("当前版本每个用户只支持一个角色");
            value = roles.get(0);
        }
        if (value == null || value.isBlank()) {
            if (required) throw ManagementException.badRequest("请选择用户角色");
            return null;
        }
        try {
            return UserRole.parse(value).code();
        } catch (IllegalArgumentException exception) {
            throw ManagementException.badRequest(exception.getMessage());
        }
    }

    private boolean resolveEnabled(String status, boolean defaultValue) {
        if (status == null || status.isBlank()) return defaultValue;
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "ACTIVE", "ENABLED" -> true;
            case "DISABLED", "INACTIVE" -> false;
            default -> throw ManagementException.badRequest("用户状态必须为 ACTIVE 或 DISABLED");
        };
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 72
                || !password.matches(".*[a-z].*") || !password.matches(".*[A-Z].*")
                || !password.matches(".*\\d.*") || !password.matches(".*[^A-Za-z0-9].*")) {
            throw ManagementException.badRequest("密码需为 8-72 位，并包含大小写字母、数字和特殊字符");
        }
    }

    private UserView toView(UserAccount user, LocalDateTime now) {
        String status = !user.isEnabled() ? "DISABLED" : user.isLocked(now) ? "LOCKED" : "ACTIVE";
        String role = UserRole.parse(user.getRole()).code();
        return new UserView(user.getId(), user.getUsername(), user.getDisplayName(), role,
                List.of(role), status, user.isEnabled(), user.getFailedLoginAttempts(),
                user.getLockedUntil(), user.getCreatedAt());
    }

    private boolean isAdmin(String role) {
        return "ADMIN".equalsIgnoreCase(role) || "WAREHOUSE_ADMIN".equalsIgnoreCase(role);
    }

    private void lockAdminSet() {
        guardRepository.lockById(SecurityGuard.ADMIN_SET_ID)
                .orElseThrow(() -> new IllegalStateException("Security guard row is missing"));
    }

    private UserAccount requireActiveAdmin(Long operatorId, boolean lock) {
        if (operatorId == null) throw ManagementException.forbidden("管理员身份已失效，请重新登录");
        UserAccount operator = (lock ? repository.lockById(operatorId) : repository.findById(operatorId))
                .orElseThrow(() -> ManagementException.forbidden("管理员身份已失效，请重新登录"));
        if (!operator.isEnabled() || operator.isLocked(LocalDateTime.now()) || !isAdmin(operator.getRole())) {
            throw ManagementException.forbidden("管理员身份已失效，请重新登录");
        }
        return operator;
    }

    public record PageResult<T>(List<T> records, long total, int page, int size) {}
    public record UserView(Long id, String username, String displayName, String role, List<String> roles,
                           String status, boolean enabled, int failedLoginAttempts,
                           LocalDateTime lockedUntil, LocalDateTime createdAt) {}
    public record RoleView(String code, String name, String scope) {}

    public static final class ManagementException extends RuntimeException {
        private final int status;
        private ManagementException(int status, String message) { super(message); this.status = status; }
        public int status() { return status; }
        static ManagementException badRequest(String message) { return new ManagementException(400, message); }
        static ManagementException forbidden(String message) { return new ManagementException(403, message); }
        static ManagementException notFound(String message) { return new ManagementException(404, message); }
        static ManagementException conflict(String message) { return new ManagementException(409, message); }
    }
}
