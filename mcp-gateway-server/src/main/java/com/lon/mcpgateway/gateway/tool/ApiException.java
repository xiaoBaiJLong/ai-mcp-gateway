package com.lon.mcpgateway.gateway.tool;

import org.springframework.http.HttpStatus;

class ApiException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    ApiException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    String code() {
        return code;
    }

    HttpStatus status() {
        return status;
    }
}
