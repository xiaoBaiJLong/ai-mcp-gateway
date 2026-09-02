package com.lon.mcpgateway.gateway.api.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.lon.mcpgateway.gateway.types.validation.ValidationModels.ToolResult;
import com.lon.mcpgateway.gateway.types.validation.ValidationModels.ValidationTool;
import java.util.List;

public interface McpGatewayValidationClientPort {
    List<ValidationTool> connect(String agentKey);

    ToolResult callTool(String agentKey, String toolName, JsonNode arguments);
}
