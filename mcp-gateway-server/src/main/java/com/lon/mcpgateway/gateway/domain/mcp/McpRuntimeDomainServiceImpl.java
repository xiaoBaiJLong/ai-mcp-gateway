package com.lon.mcpgateway.gateway.domain.mcp;

import com.lon.mcpgateway.gateway.api.agent.AgentRepositoryPort;
import com.lon.mcpgateway.gateway.api.mcp.McpRuntimeDomainService;
import com.lon.mcpgateway.gateway.api.mcp.McpToolInvocationPort;
import com.lon.mcpgateway.gateway.api.tool.McpToolRepositoryPort;
import com.lon.mcpgateway.gateway.types.agent.AgentModels.RuntimeAgentRecord;
import com.lon.mcpgateway.gateway.types.common.GatewayException;
import com.lon.mcpgateway.gateway.types.tool.ToolModels.RuntimeToolRecord;
import com.lon.mcpgateway.gateway.types.mcp.McpRuntimeModels.ToolInvocationResult;
import com.lon.mcpgateway.gateway.types.mcp.McpRuntimeModels.UserContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public final class McpRuntimeDomainServiceImpl implements McpRuntimeDomainService {
    private final AgentRepositoryPort agentRepository;
    private final McpToolRepositoryPort toolRepository;
    private final McpToolInvocationPort toolInvocation;
    private final ObjectMapper objectMapper;
    private final McpToolInputValidator inputValidator = new McpToolInputValidator();

    public McpRuntimeDomainServiceImpl(AgentRepositoryPort agentRepository, McpToolRepositoryPort toolRepository,
            McpToolInvocationPort toolInvocation, ObjectMapper objectMapper) {
        this.agentRepository = agentRepository;
        this.toolRepository = toolRepository;
        this.toolInvocation = toolInvocation;
        this.objectMapper = objectMapper;
    }

    @Override
    public void requireEnabledAgent(String apiKey) {
        agent(apiKey);
    }

    @Override
    public List<RuntimeToolRecord> listEnabledTools(String apiKey) {
        return toolRepository.findEnabledToolsForAgent(agent(apiKey).id());
    }

    @Override
    public RuntimeToolRecord requireEnabledTool(String apiKey, String toolName) {
        RuntimeToolRecord tool = toolRepository.findEnabledToolForAgent(agent(apiKey).id(), toolName);
        if (tool == null) {
            throw new GatewayException("INVALID_TOOL_REQUEST", "无效的 Tool 调用参数");
        }
        return tool;
    }

    @Override
    public ToolInvocationResult callTool(String apiKey, String toolName, JsonNode arguments, UserContext userContext) {
        RuntimeToolRecord tool = requireEnabledTool(apiKey, toolName);
        JsonNode schema = readSchema(tool.inputSchema());
        String error = inputValidator.validate(schema, arguments);
        if (error != null) {
            return new ToolInvocationResult(400, objectMapper.nullNode(), "INVALID_ARGUMENT");
        }
        return toolInvocation.invoke(tool, arguments, userContext);
    }

    private JsonNode readSchema(String schema) {
        try {
            return objectMapper.readTree(schema);
        } catch (Exception exception) {
            throw new IllegalStateException("持久化的 Tool inputSchema 无效", exception);
        }
    }

    private RuntimeAgentRecord agent(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new GatewayException("INVALID_AGENT_CREDENTIAL", "智能体凭证无效");
        }
        RuntimeAgentRecord agent = agentRepository.findEnabledAgentByKeyHash(hash(apiKey));
        if (agent == null) {
            throw new GatewayException("INVALID_AGENT_CREDENTIAL", "智能体凭证无效");
        }
        return agent;
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte item : digest) {
                hex.append(String.format("%02x", item));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 缺少 SHA-256", exception);
        }
    }
}
