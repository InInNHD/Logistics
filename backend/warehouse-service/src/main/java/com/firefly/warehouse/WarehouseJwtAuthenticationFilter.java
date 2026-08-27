package com.firefly.warehouse;

import com.firefly.common.security.JwtService;
import com.firefly.common.security.TokenClaims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
class WarehouseJwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Set<String> IDENTITY_HEADERS = Set.of("x-user-id", "x-username", "x-user-role");
    private final JwtService jwtService;

    WarehouseJwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            SecurityContextHolder.clearContext();
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest authenticatedRequest = request;
        try {
            TokenClaims claims = jwtService.parse(authorization.substring(7));
            String role = normalizeRole(claims.role());
            if (claims.userId() == null || claims.username() == null || claims.username().isBlank() || role.isBlank()) {
                throw new IllegalArgumentException("Incomplete JWT claims");
            }
            var authentication = new UsernamePasswordAuthenticationToken(
                    claims,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role)));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            authenticatedRequest = new TrustedIdentityRequest(request, claims, role);
        } catch (RuntimeException exception) {
            SecurityContextHolder.clearContext();
        }
        chain.doFilter(authenticatedRequest, response);
    }

    private static String normalizeRole(String role) {
        if (role == null) return "";
        String normalized = role.toUpperCase(Locale.ROOT);
        return "WAREHOUSE_ADMIN".equals(normalized) ? "ADMIN" : normalized;
    }

    private static final class TrustedIdentityRequest extends HttpServletRequestWrapper {
        private final String userId;
        private final String username;
        private final String role;

        private TrustedIdentityRequest(HttpServletRequest request, TokenClaims claims, String role) {
            super(request);
            this.userId = claims.userId().toString();
            this.username = claims.username();
            this.role = role;
        }

        @Override
        public String getHeader(String name) {
            if (name.equalsIgnoreCase("X-User-Id")) return userId;
            if (name.equalsIgnoreCase("X-Username")) return username;
            if (name.equalsIgnoreCase("X-User-Role")) return role;
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            String value = getHeader(name);
            if (isIdentityHeader(name)) return Collections.enumeration(List.of(value));
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            LinkedHashSet<String> names = new LinkedHashSet<>();
            Enumeration<String> original = super.getHeaderNames();
            while (original.hasMoreElements()) {
                String name = original.nextElement();
                if (!isIdentityHeader(name)) names.add(name);
            }
            names.add("X-User-Id");
            names.add("X-Username");
            names.add("X-User-Role");
            return Collections.enumeration(new ArrayList<>(names));
        }

        private static boolean isIdentityHeader(String name) {
            return name != null && IDENTITY_HEADERS.contains(name.toLowerCase(Locale.ROOT));
        }
    }
}
