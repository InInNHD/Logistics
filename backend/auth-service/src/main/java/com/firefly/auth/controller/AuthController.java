package com.firefly.auth.controller;

import com.firefly.auth.service.AuthService;
import com.firefly.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.firefly.common.security.TokenClaims;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    public AuthController(AuthService authService) { this.authService = authService; }

    @PostMapping("/login") public ApiResponse<AuthService.LoginResult> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request.username(), request.password()));
    }
    @GetMapping("/me") public ApiResponse<AuthService.UserInfo> me(Authentication authentication) {
        TokenClaims claims = (TokenClaims) authentication.getPrincipal();
        return ApiResponse.ok(authService.currentUser(claims.userId()));
    }

    @ExceptionHandler(AuthService.LoginException.class)
    ResponseEntity<ApiResponse<Void>> invalidCredentials(AuthService.LoginException exception) {
        return ResponseEntity.status(exception.code()).body(ApiResponse.fail(exception.code(), exception.getMessage()));
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
}
