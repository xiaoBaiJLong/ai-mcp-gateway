package com.lon.mcpgateway.gateway.infrastructure.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lon.mcpgateway.gateway.api.validation.McpGatewayValidationClientPort;
import com.lon.mcpgateway.gateway.types.common.GatewayException;
import com.lon.mcpgateway.gateway.types.validation.ValidationModels.ToolResult;
import com.lon.mcpgateway.gateway.types.validation.ValidationModels.ValidationTool;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
class HttpMcpGatewayValidationClient implements McpGatewayValidationClientPort {
    private static final String PROTOCOL_VERSION = "2025-11-25";
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final AtomicLong requestId = new AtomicLong();

    HttpMcpGatewayValidationClient(WebClient.Builder webClientBuilder, ObjectMapper objectMapper,
            @Value("${gateway.validation.mcp-endpoint:http://127.0.0.1:${server.port:8080}/mcp}") String endpoint) {
        this.webClient = webClientBuilder.baseUrl(endpoint).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ValidationTool> connect(String agentKey) {
        request(agentKey, "initialize", Map.of("protocolVersion", PROTOCOL_VERSION, "capabilities", Map.of(),
                "clientInfo", Map.of("name", "mcp-validation-console", "version", "0.0.1")));
        JsonNode response = request(agentKey, "tools/list", Map.of());
        List<ValidationTool> tools = new ArrayList<>();
        for (JsonNode tool : response.path("result").path("tools")) {
            tools.add(new ValidationTool(tool.path("name").asText(), tool.path("description").asText(), tool.path("inputSchema").toString()));
        }
        return tools;
    }

    @Override
    public ToolResult callTool(String agentKey, String toolName, JsonNode arguments) {
        JsonNode response = request(agentKey, "tools/call", Map.of("name", toolName, "arguments", arguments));
        JsonNode result = response.path("result");
        if (result.isMissingNode()) {
            throw new GatewayException("VALIDATION_MCP_FAILURE", "MCP Tool 调用未返回结果");
        }
        JsonNode content = result.path("structuredContent");
        return new ToolResult(content.path("httpStatus").asInt(), content.path("body"),
                content.has("gatewayError") ? content.path("gatewayError").asText() : null, result.path("isError").asBoolean());
    }

    private JsonNode request(String agentKey, String method, Object params) {
        try {
            return webClient.post().contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                    .header("X-MCP-Agent-Key", agentKey).header("MCP-Protocol-Version", PROTOCOL_VERSION)
                    .bodyValue(Map.of("jsonrpc", "2.0", "id", requestId.incrementAndGet(), "method", method, "params", params))
                    .exchangeToMono(response -> {
                        if (response.statusCode().value() == 401) {
                            return response.createException().flatMap(error -> reactor.core.publisher.Mono.error(
                                    new GatewayException("VALIDATION_CREDENTIAL_INVALID", "智能体凭证无效或已禁用")));
                        }
                        if (!response.statusCode().is2xxSuccessful()) {
                            return response.createException().flatMap(error -> reactor.core.publisher.Mono.error(
                                    new GatewayException("VALIDATION_MCP_FAILURE", "无法建立 MCP 验证连接")));
                        }
                        return response.bodyToMono(JsonNode.class);
                    }).block();
        } catch (GatewayException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new GatewayException("VALIDATION_MCP_FAILURE", "无法建立 MCP 验证连接");
        }
    }
}
