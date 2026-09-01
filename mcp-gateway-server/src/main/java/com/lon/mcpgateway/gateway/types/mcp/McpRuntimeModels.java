package com.lon.mcpgateway.gateway.types.mcp;

import com.fasterxml.jackson.databind.JsonNode;

public final class McpRuntimeModels {
    private McpRuntimeModels() {
    }

    public record UserContext(String userId, String tenantId) {
    }

    public record ToolInvocationResult(int httpStatus, JsonNode body, String gatewayError) {
        public boolean isError() {
            return gatewayError != null || httpStatus < 200 || httpStatus >= 300;
        }
    }
}
