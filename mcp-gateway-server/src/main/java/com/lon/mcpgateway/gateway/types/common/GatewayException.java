package com.lon.mcpgateway.gateway.types.common;

public class GatewayException extends RuntimeException {
    private final String code;

    public GatewayException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }

}
