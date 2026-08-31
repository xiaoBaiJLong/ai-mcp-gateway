package com.lon.mcpgateway.gateway.tool;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiResponse<Void>> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.status()).body(ApiResponse.failure(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(OpenApiImportException.class)
    ResponseEntity<ApiResponse<Void>> handleImportException(OpenApiImportException exception) {
        return ResponseEntity.unprocessableEntity().body(ApiResponse.failure("OPENAPI_IMPORT_FAILED", exception.getMessage()));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    ResponseEntity<ApiResponse<Void>> handleValidationException(WebExchangeBindException exception) {
        String message = exception.getAllErrors().stream().findFirst().map(ObjectError::getDefaultMessage).orElse("请求参数无效");
        return ResponseEntity.badRequest().body(ApiResponse.failure("INVALID_ARGUMENT", message));
    }
}
