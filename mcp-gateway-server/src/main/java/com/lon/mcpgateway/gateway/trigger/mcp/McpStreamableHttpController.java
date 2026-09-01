package com.lon.mcpgateway.gateway.trigger.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lon.mcpgateway.gateway.api.mcp.McpRuntimeDomainService;
import com.lon.mcpgateway.gateway.types.common.GatewayException;
import com.lon.mcpgateway.gateway.types.mcp.McpRuntimeModels.ToolInvocationResult;
import com.lon.mcpgateway.gateway.types.mcp.McpRuntimeModels.UserContext;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/mcp")
class McpStreamableHttpController {
    private static final String PROTOCOL_VERSION = "2025-11-25";
    private final McpRuntimeDomainService runtime;
    private final ObjectMapper objectMapper;

    McpStreamableHttpController(McpRuntimeDomainService runtime, ObjectMapper objectMapper) {
        this.runtime = runtime;
        this.objectMapper = objectMapper;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    Mono<ResponseEntity<McpSchema.JSONRPCResponse>> post(@RequestHeader(name = "X-MCP-Agent-Key", required = false) String agentKey,
            @RequestHeader(name = "MCP-Protocol-Version", required = false) String protocolVersion,
            @RequestHeader(name = "X-User-Id", required = false) String userId,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @RequestBody JsonNode request) {
        return Mono.fromCallable(() -> response(agentKey, protocolVersion, userId, tenantId, request)).subscribeOn(Schedulers.boundedElastic());
    }

    private ResponseEntity<McpSchema.JSONRPCResponse> response(String agentKey, String protocolVersion, String userId, String tenantId,
            JsonNode request) {
        try {
            runtime.requireEnabledAgent(agentKey);
        } catch (GatewayException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Object id = id(request.path("id"));
        String method = request.path("method").asText();
        if (protocolVersion != null && !PROTOCOL_VERSION.equals(protocolVersion)) {
            return ResponseEntity.badRequest().header("MCP-Protocol-Version", PROTOCOL_VERSION)
                    .body(error(id, McpSchema.ErrorCodes.INVALID_PARAMS, "Unsupported MCP protocol version"));
        }
        if ("2.0".equals(request.path("jsonrpc").asText()) && id == null && method.startsWith("notifications/")) {
            return ResponseEntity.accepted().header("MCP-Protocol-Version", PROTOCOL_VERSION).build();
        }
        if (!"2.0".equals(request.path("jsonrpc").asText()) || id == null || method.isBlank()) {
            return ok(error(id, McpSchema.ErrorCodes.INVALID_REQUEST, "Invalid JSON-RPC request"));
        }
        return switch (request.path("method").asText()) {
            case McpSchema.METHOD_INITIALIZE -> initialize(id, request.path("params"));
            case McpSchema.METHOD_TOOLS_LIST -> ok(McpSchema.JSONRPCResponse.result(id, Map.of("tools",
                    runtime.listEnabledTools(agentKey).stream().map(tool -> Map.of("name", tool.name(), "description", tool.description(),
                            "inputSchema", objectMapper.convertValue(readSchema(tool.inputSchema()), Map.class))).toList())));
            case McpSchema.METHOD_TOOLS_CALL -> toolCall(id, agentKey, userId, tenantId, request.path("params"));
            default -> ok(error(id, McpSchema.ErrorCodes.METHOD_NOT_FOUND, "Method not found"));
        };
    }

    private ResponseEntity<McpSchema.JSONRPCResponse> initialize(Object id, JsonNode params) {
        if (!PROTOCOL_VERSION.equals(params.path("protocolVersion").asText())) {
            return ok(error(id, McpSchema.ErrorCodes.INVALID_PARAMS, "Unsupported MCP protocol version"));
        }
        return ok(McpSchema.JSONRPCResponse.result(id, Map.of("protocolVersion", PROTOCOL_VERSION,
                "capabilities", Map.of("tools", Map.of()), "serverInfo", Map.of("name", "mcp-gateway", "version", "0.0.1"))));
    }

    private ResponseEntity<McpSchema.JSONRPCResponse> toolCall(Object id, String agentKey, String userId, String tenantId, JsonNode params) {
        try {
            String toolName = params.path("name").asText();
            if (toolName.isBlank()) {
                throw new GatewayException("INVALID_TOOL_REQUEST", "无效的 Tool 调用参数");
            }
            ToolInvocationResult result = runtime.callTool(agentKey, toolName, params.path("arguments"), new UserContext(userId, tenantId));
            return ok(McpSchema.JSONRPCResponse.result(id, toolResult(result)));
        } catch (GatewayException exception) {
            return ok(error(id, McpSchema.ErrorCodes.INVALID_PARAMS, "Invalid tool request"));
        }
    }

    private ResponseEntity<McpSchema.JSONRPCResponse> ok(McpSchema.JSONRPCResponse response) {
        return ResponseEntity.ok().header("MCP-Protocol-Version", PROTOCOL_VERSION).body(response);
    }

    private Map<String, Object> toolResult(ToolInvocationResult result) {
        Map<String, Object> envelope = new java.util.LinkedHashMap<>();
        envelope.put("httpStatus", result.httpStatus());
        envelope.put("body", result.body());
        if (result.gatewayError() != null) {
            envelope.put("gatewayError", result.gatewayError());
        }
        String text;
        try {
            text = objectMapper.writeValueAsString(envelope);
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成 MCP Tool 结果", exception);
        }
        return Map.of("content", List.of(Map.of("type", "text", "text", text)), "isError", result.isError(), "structuredContent", envelope);
    }

    private JsonNode readSchema(String schema) {
        try {
            return objectMapper.readTree(schema);
        } catch (Exception exception) {
            throw new IllegalStateException("持久化的 Tool inputSchema 无效", exception);
        }
    }

    private Object id(JsonNode id) {
        if (id.isTextual()) {
            return id.asText();
        }
        return id.isIntegralNumber() ? id.longValue() : null;
    }

    private McpSchema.JSONRPCResponse error(Object id, int code, String message) {
        if (id == null) {
            id = 0L;
        }
        return McpSchema.JSONRPCResponse.error(id, new McpSchema.JSONRPCResponse.JSONRPCError(code, message, null));
    }
}
