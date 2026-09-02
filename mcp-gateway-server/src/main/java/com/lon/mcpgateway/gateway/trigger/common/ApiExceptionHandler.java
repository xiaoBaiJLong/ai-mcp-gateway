package com.lon.mcpgateway.gateway.trigger.common;

import com.lon.mcpgateway.gateway.types.common.GatewayException;
import com.lon.mcpgateway.gateway.types.common.OpenApiImportException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(GatewayException.class)
    ResponseEntity<ApiResponse<Void>> handleGatewayException(GatewayException exception) {
        return ResponseEntity.status(status(exception.code())).body(ApiResponse.failure(exception.code(), exception.getMessage()));
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

    private HttpStatus status(String code) {
        return switch (code) {
            case "AGENT_NOT_FOUND", "CREDENTIAL_NOT_FOUND", "OPERATION_NOT_FOUND", "TOOL_NOT_FOUND", "TOOL_COLLECTION_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "TOOL_NAME_EXISTS", "TOOL_SOURCE_EXISTS" -> HttpStatus.CONFLICT;
            case "TOOL_NOT_PUBLISHED", "OPERATION_UNSUPPORTED" -> HttpStatus.UNPROCESSABLE_ENTITY;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
