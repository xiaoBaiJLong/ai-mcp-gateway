package com.lon.mcpgateway.gateway.api.mcp;

import com.lon.mcpgateway.gateway.types.tool.ToolModels.RuntimeToolRecord;
import com.lon.mcpgateway.gateway.types.mcp.McpRuntimeModels.ToolInvocationResult;
import com.lon.mcpgateway.gateway.types.mcp.McpRuntimeModels.UserContext;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public interface McpRuntimeDomainService {
    void requireEnabledAgent(String apiKey);

    List<RuntimeToolRecord> listEnabledTools(String apiKey);

    RuntimeToolRecord requireEnabledTool(String apiKey, String toolName);

    ToolInvocationResult callTool(String apiKey, String toolName, JsonNode arguments, UserContext userContext);
}
