package com.xiaobaijlong.mcpgateway.management.tool;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ToolManagementExceptionHandler {

    @ExceptionHandler(ToolManagementException.class)
    ResponseEntity<Map<String, String>> handle(ToolManagementException exception) {
        return ResponseEntity.status(exception.status()).body(Map.of(
                "code", exception.code(),
                "message", exception.getMessage()
        ));
    }
}
