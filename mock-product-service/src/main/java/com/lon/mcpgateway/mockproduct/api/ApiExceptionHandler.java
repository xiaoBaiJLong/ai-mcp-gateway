package com.lon.mcpgateway.mockproduct.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebInputException;

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler({WebExchangeBindException.class, ServerWebInputException.class})
    org.springframework.http.ResponseEntity<ErrorResponse> invalidRequest(Exception exception) {
        return org.springframework.http.ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_REQUEST", "请求参数无效"));
    }
    record ErrorResponse(String code, String message) {}
}
