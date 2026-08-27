package com.firefly.auth.controller;

import com.firefly.auth.service.UserManagementService;
import com.firefly.common.api.ApiResponse;
import com.firefly.common.security.TokenClaims;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class UserAdminController {
    private final UserManagementService service;

    public UserAdminController(UserManagementService service) { this.service = service; }

    @GetMapping("/users")
    public ApiResponse<UserManagementService.PageResult<UserManagementService.UserView>> users(
            Authentication authentication,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "role", required = false) String role,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(service.list(operatorId(authentication), keyword, status, role, page, size));
    }

    @PostMapping("/users")
    public ApiResponse<UserManagementService.UserView> create(
            Authentication authentication,
            @Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.ok("用户创建成功", service.create(operatorId(authentication), request.username(), request.password(),
                request.displayName(), request.role(), request.roles(), request.status()));
    }

    @PatchMapping("/users/{id}")
    public ApiResponse<UserManagementService.UserView> update(
            @PathVariable(name = "id") Long id,
            Authentication authentication,
            @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.ok("用户更新成功", service.update(id, operatorId(authentication), request.displayName(), request.role(),
                request.roles(), request.status(), request.password()));
    }

    @GetMapping("/roles")
    public ApiResponse<List<UserManagementService.RoleView>> roles(Authentication authentication) {
        return ApiResponse.ok(service.roles(operatorId(authentication)));
    }

    @GetMapping("/audit-events")
    public ApiResponse<UserManagementService.PageResult<UserManagementService.AuditView>> auditEvents(
            Authentication authentication,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(service.auditEvents(operatorId(authentication), page, size));
    }

    private Long operatorId(Authentication authentication) {
        return ((TokenClaims) authentication.getPrincipal()).userId();
    }

    @ExceptionHandler(UserManagementService.ManagementException.class)
    ResponseEntity<ApiResponse<Void>> managementError(UserManagementService.ManagementException exception) {
        return ResponseEntity.status(exception.status()).body(ApiResponse.fail(exception.status(), exception.getMessage()));
    }

    public record CreateUserRequest(
            @NotBlank @Size(min = 3, max = 64)
            @Pattern(regexp = "[A-Za-z0-9_.-]+", message = "用户名只能包含字母、数字、下划线、点和连字符") String username,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 100) String displayName,
            String role,
            List<String> roles,
            String status) {}

    public record UpdateUserRequest(
            @Size(max = 100) String displayName,
            String role,
            List<String> roles,
            String status,
            @Size(min = 8, max = 72) String password) {}
}
