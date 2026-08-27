package com.firefly.auth.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sys_auth_audit")
public class AuthAuditEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 40) private String eventType;
    @Column(nullable = false, length = 64) private String username;
    @Column(nullable = false) private boolean success;
    @Column(length = 255) private String detail;
    @Column(nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
    protected AuthAuditEvent() {}
    public AuthAuditEvent(String eventType, String username, boolean success, String detail) {
        this.eventType = eventType; this.username = username; this.success = success; this.detail = detail;
    }
    public Long getId() { return id; }
    public String getEventType() { return eventType; }
    public String getUsername() { return username; }
    public boolean isSuccess() { return success; }
    public String getDetail() { return detail; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
