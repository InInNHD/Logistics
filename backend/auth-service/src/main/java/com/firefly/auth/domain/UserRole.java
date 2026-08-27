package com.firefly.auth.domain;

import java.util.Locale;

public enum UserRole {
    ADMIN("ADMIN", "系统管理员", "全部仓库与系统配置"),
    WAREHOUSE_MANAGER("WAREHOUSE_MANAGER", "仓库主管", "仓库资料与全部仓储作业"),
    RECEIVER("RECEIVER", "收货员", "入库建单与收货"),
    PICKER("PICKER", "拣货员", "出库建单、分配与发运");

    private final String code;
    private final String name;
    private final String scope;

    UserRole(String code, String name, String scope) {
        this.code = code;
        this.name = name;
        this.scope = scope;
    }

    public String code() { return code; }
    public String displayName() { return name; }
    public String scope() { return scope; }

    public static UserRole parse(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("角色不能为空");
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("WAREHOUSE_ADMIN".equals(normalized)) normalized = "ADMIN";
        try {
            return UserRole.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("不支持的角色：" + value);
        }
    }
}
