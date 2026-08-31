package com.lon.mcpgateway.gateway.types.common;

public class OpenApiImportException extends RuntimeException {
    public OpenApiImportException(String message) {
        super(message);
    }

    public OpenApiImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
