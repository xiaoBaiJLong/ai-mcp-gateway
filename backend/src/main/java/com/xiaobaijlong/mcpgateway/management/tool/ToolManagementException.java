package com.xiaobaijlong.mcpgateway.management.tool;

import org.springframework.http.HttpStatus;

public class ToolManagementException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ToolManagementException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
