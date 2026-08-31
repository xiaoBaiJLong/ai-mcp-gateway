package com.lon.mcpgateway.gateway.tool;

record ApiResponse<T>(String code, String message, T data) {
    static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("OK", "", data);
    }

    static ApiResponse<Void> failure(String code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
