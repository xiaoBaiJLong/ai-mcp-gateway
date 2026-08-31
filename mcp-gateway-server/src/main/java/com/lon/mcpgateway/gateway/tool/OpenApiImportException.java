package com.lon.mcpgateway.gateway.tool;

class OpenApiImportException extends RuntimeException {
    OpenApiImportException(String message) {
        super(message);
    }

    OpenApiImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
