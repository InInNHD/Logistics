package com.firefly.auth.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sys_user")
public class UserAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 64) private String username;
    @Column(nullable = false, length = 100) private String passwordHash;
    @Column(nullable = false, length = 100) private String displayName;
    @Column(nullable = false, length = 30) private String role;
    @Column(nullable = false) private boolean enabled = true;
    @Column(nullable = false) private int failedLoginAttempts;
    private LocalDateTime lockedUntil;
    @Column(nullable = false) private LocalDateTime createdAt = LocalDateTime.now();

    protected UserAccount() {}
    public UserAccount(String username, String passwordHash, String displayName, String role) {
        this.username = username; this.passwordHash = passwordHash; this.displayName = displayName; this.role = role;
    }
    public Long getId() { return id; } public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; } public String getDisplayName() { return displayName; }
    public String getRole() { return role; } public boolean isEnabled() { return enabled; }
    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public LocalDateTime getLockedUntil() { return lockedUntil; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public boolean isLocked(LocalDateTime now) { return lockedUntil != null && lockedUntil.isAfter(now); }
    public void loginSucceeded() { failedLoginAttempts = 0; lockedUntil = null; }
    public void loginFailed(int maxFailures, LocalDateTime lockedUntil) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= maxFailures) this.lockedUntil = lockedUntil;
    }
    public void updateProfile(String displayName, String role, Boolean enabled) {
        if (displayName != null) this.displayName = displayName;
        if (role != null) this.role = role;
        if (enabled != null) this.enabled = enabled;
        if (Boolean.TRUE.equals(enabled)) loginSucceeded();
    }
    public void resetPassword(String passwordHash) { this.passwordHash = passwordHash; loginSucceeded(); }
}
