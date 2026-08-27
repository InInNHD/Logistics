package com.firefly.common.api;

public record ApiResponse<T>(int code, String message, T data) {
    public static <T> ApiResponse<T> ok(T data) { return new ApiResponse<>(0, "success", data); }
    public static <T> ApiResponse<T> ok(String message, T data) { return new ApiResponse<>(0, message, data); }
    public static ApiResponse<Void> fail(int code, String message) { return new ApiResponse<>(code, message, null); }
}
