package com.firefly.auth.controller;

import com.firefly.auth.service.AuthService;
import com.firefly.auth.service.UserManagementService;
import com.firefly.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.firefly.common.security.TokenClaims;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final UserManagementService users;
    public AuthController(AuthService authService, UserManagementService users) {
        this.authService = authService;
        this.users = users;
    }

    @PostMapping("/login") public ApiResponse<AuthService.LoginResult> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request.username(), request.password()));
    }
    @PostMapping("/register") public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        users.register(request.username(), request.password(), request.displayName());
        return ApiResponse.ok("申请已提交，请联系管理员启用账号", null);
    }
    @PostMapping("/logout") public ApiResponse<Void> logout(
            @RequestHeader(name = "Authorization") String authorization) {
        authService.logout(authorization.substring(7));
        return ApiResponse.ok("已安全退出", null);
    }
    @PostMapping("/token-status") public ApiResponse<Boolean> tokenStatus(@Valid @RequestBody TokenStatusRequest request) {
        return ApiResponse.ok(authService.tokenActive(request.userId(), request.role(), request.tokenId()));
    }
    @GetMapping("/me") public ApiResponse<AuthService.UserInfo> me(Authentication authentication) {
        TokenClaims claims = (TokenClaims) authentication.getPrincipal();
        return ApiResponse.ok(authService.currentUser(claims.userId()));
    }

    @ExceptionHandler(AuthService.LoginException.class)
    ResponseEntity<ApiResponse<Void>> invalidCredentials(AuthService.LoginException exception) {
        return ResponseEntity.status(exception.code()).body(ApiResponse.fail(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(UserManagementService.ManagementException.class)
    ResponseEntity<ApiResponse<Void>> registrationError(UserManagementService.ManagementException exception) {
        return ResponseEntity.status(exception.status()).body(ApiResponse.fail(exception.status(), exception.getMessage()));
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 64)
            @Pattern(regexp = "[A-Za-z0-9_.-]+", message = "用户名只能包含字母、数字、下划线、点和连字符") String username,
            @NotBlank @Size(min = 2, max = 100) String displayName,
            @NotBlank @Size(min = 8, max = 72) String password) {}
    public record TokenStatusRequest(@jakarta.validation.constraints.NotNull Long userId,
                                     @NotBlank String role, @NotBlank String tokenId) {}
}
