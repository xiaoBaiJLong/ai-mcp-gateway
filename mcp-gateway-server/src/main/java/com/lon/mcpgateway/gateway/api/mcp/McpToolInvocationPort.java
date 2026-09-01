package com.lon.mcpgateway.gateway.api.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.lon.mcpgateway.gateway.types.mcp.McpRuntimeModels.ToolInvocationResult;
import com.lon.mcpgateway.gateway.types.mcp.McpRuntimeModels.UserContext;
import com.lon.mcpgateway.gateway.types.tool.ToolModels.RuntimeToolRecord;

public interface McpToolInvocationPort {
    ToolInvocationResult invoke(RuntimeToolRecord tool, JsonNode arguments, UserContext userContext);
}
