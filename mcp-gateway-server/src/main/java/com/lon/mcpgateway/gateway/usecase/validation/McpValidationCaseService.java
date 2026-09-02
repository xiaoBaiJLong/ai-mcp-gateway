package com.lon.mcpgateway.gateway.usecase.validation;

import com.lon.mcpgateway.gateway.api.validation.McpGatewayValidationClientPort;
import com.lon.mcpgateway.gateway.api.validation.McpValidationCase;
import com.lon.mcpgateway.gateway.api.validation.OpenAiValidationChatbotPort;
import com.lon.mcpgateway.gateway.types.validation.ValidationModels.ChatRequest;
import com.lon.mcpgateway.gateway.types.validation.ValidationModels.ChatbotEvent;
import com.lon.mcpgateway.gateway.types.validation.ValidationModels.ChatbotRequest;
import com.lon.mcpgateway.gateway.types.validation.ValidationModels.ConnectionView;
import com.lon.mcpgateway.gateway.types.validation.ValidationModels.ToolCallEvent;
import com.lon.mcpgateway.gateway.types.validation.ValidationModels.ToolCallResult;
import com.lon.mcpgateway.gateway.types.validation.ValidationModels.ValidationEvent;
import com.lon.mcpgateway.gateway.types.validation.ValidationModels.ValidationTool;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import reactor.core.publisher.Flux;

public final class McpValidationCaseService implements McpValidationCase {
    private static final int MAX_TOOL_CALLS = 3;
    private final McpGatewayValidationClientPort mcpClient;
    private final OpenAiValidationChatbotPort chatbot;

    public McpValidationCaseService(McpGatewayValidationClientPort mcpClient, OpenAiValidationChatbotPort chatbot) {
        this.mcpClient = mcpClient;
        this.chatbot = chatbot;
    }

    @Override
    public ConnectionView connect(String agentKey) {
        return new ConnectionView(mcpClient.connect(agentKey));
    }

    @Override
    public Flux<ValidationEvent> chat(ChatRequest request) {
        List<ValidationTool> tools = mcpClient.connect(request.agentKey());
        List<com.lon.mcpgateway.gateway.types.validation.ValidationModels.ConversationMessage> context = recentMessages(request.messages());
        return run(request.agentKey(), context, tools, List.of(), 0);
    }

    private Flux<ValidationEvent> run(String agentKey,
            List<com.lon.mcpgateway.gateway.types.validation.ValidationModels.ConversationMessage> messages,
            List<ValidationTool> tools, List<ToolCallResult> priorResults, int callCount) {
        List<ToolCallEvent> calls = new ArrayList<>();
        return chatbot.respond(new ChatbotRequest(messages, tools, priorResults)).<ValidationEvent>handle((event, sink) -> {
            if (event instanceof com.lon.mcpgateway.gateway.types.validation.ValidationModels.TextDelta text) {
                sink.next(new ValidationEvent("text", text.text()));
            } else if (event instanceof ToolCallEvent call) {
                calls.add(call);
            }
        }).concatWith(Flux.defer(() -> {
            List<ValidationEvent> visible = new ArrayList<>();
            int remaining = MAX_TOOL_CALLS - callCount;
            if (calls.isEmpty()) {
                return Flux.fromIterable(visible);
            }
            if (remaining == 0) {
                visible.add(new ValidationEvent("limit", "本轮对话最多自动执行 3 次 Tool 调用"));
                return Flux.fromIterable(visible);
            }
            List<ToolCallResult> results = new ArrayList<>();
            for (ToolCallEvent call : calls.subList(0, Math.min(remaining, calls.size()))) {
                var toolCall = call.toolCall();
                visible.add(new ValidationEvent("tool_call", toolCall));
                visible.add(new ValidationEvent("tool_status", "执行中"));
                var result = callTool(agentKey, toolCall.name(), toolCall.arguments());
                ToolCallResult stored = new ToolCallResult(toolCall.id(), toolCall.name(), toolCall.arguments(), result);
                results.add(stored);
                visible.add(new ValidationEvent("tool_result", stored));
            }
            if (calls.size() > remaining) {
                visible.add(new ValidationEvent("limit", "本轮对话最多自动执行 3 次 Tool 调用"));
                return Flux.fromIterable(visible);
            }
            return Flux.concat(Flux.fromIterable(visible), run(agentKey, messages, tools, results, callCount + results.size()));
        }));
    }

    private com.lon.mcpgateway.gateway.types.validation.ValidationModels.ToolResult callTool(String agentKey, String name,
            com.fasterxml.jackson.databind.JsonNode arguments) {
        try {
            return mcpClient.callTool(agentKey, name, arguments);
        } catch (RuntimeException exception) {
            return new com.lon.mcpgateway.gateway.types.validation.ValidationModels.ToolResult(502, JsonNodeFactory.instance.nullNode(),
                    "VALIDATION_MCP_FAILURE", true);
        }
    }

    private List<com.lon.mcpgateway.gateway.types.validation.ValidationModels.ConversationMessage> recentMessages(
            List<com.lon.mcpgateway.gateway.types.validation.ValidationModels.ConversationMessage> messages) {
        int start = Math.max(0, messages.size() - 10);
        return List.copyOf(messages.subList(start, messages.size()));
    }
}
