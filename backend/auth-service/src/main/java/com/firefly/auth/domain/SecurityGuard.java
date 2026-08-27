package com.firefly.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "sys_security_guard")
public class SecurityGuard {
    public static final long ADMIN_SET_ID = 1L;

    @Id
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String guardName;

    protected SecurityGuard() {}

    public SecurityGuard(Long id, String guardName) {
        this.id = id;
        this.guardName = guardName;
    }

    public Long getId() { return id; }
    public String getGuardName() { return guardName; }
}
