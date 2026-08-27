package com.firefly.common.security;

public record TokenClaims(Long userId, String username, String role) {}
