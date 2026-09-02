package com.lon.mcpgateway.gateway.types.validation;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public final class ValidationModels {
    private ValidationModels() {
    }

    public record ConnectRequest(@NotBlank String agentKey) {
    }

    public record ValidationTool(String name, String description, String inputSchema) {
    }

    public record ConnectionView(List<ValidationTool> tools) {
    }

    public record ConversationMessage(@NotBlank String role, @NotBlank String content) {
    }

    public record ChatRequest(@NotBlank String agentKey, List<ConversationMessage> messages) {
        public ChatRequest {
            messages = messages == null ? List.of() : List.copyOf(messages);
        }
    }

    public record ToolCall(String id, String name, JsonNode arguments) {
    }

    public record ToolResult(int httpStatus, JsonNode body, String gatewayError, boolean isError) {
    }

    public record ChatbotRequest(List<ConversationMessage> messages, List<ValidationTool> tools, List<ToolCallResult> toolResults) {
    }

    public record ToolCallResult(String toolCallId, String name, JsonNode arguments, ToolResult result) {
    }

    public sealed interface ChatbotEvent permits TextDelta, ToolCallEvent {
    }

    public record TextDelta(String text) implements ChatbotEvent {
    }

    public record ToolCallEvent(ToolCall toolCall) implements ChatbotEvent {
    }

    public record ValidationEvent(String type, Object data) {
    }
}
