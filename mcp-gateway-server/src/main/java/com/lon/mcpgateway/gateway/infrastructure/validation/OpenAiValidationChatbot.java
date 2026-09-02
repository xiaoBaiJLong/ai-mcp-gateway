package com.lon.mcpgateway.gateway.infrastructure.validation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lon.mcpgateway.gateway.api.validation.OpenAiValidationChatbotPort;
import com.lon.mcpgateway.gateway.api.validation.ValidationModelSettingsPort;
import com.lon.mcpgateway.gateway.types.common.GatewayException;
import com.lon.mcpgateway.gateway.types.validation.ValidationModels.ChatbotEvent;
import com.lon.mcpgateway.gateway.types.validation.ValidationModels.ChatbotRequest;
import com.lon.mcpgateway.gateway.types.validation.ValidationModels.TextDelta;
import com.lon.mcpgateway.gateway.types.validation.ValidationModels.ToolCall;
import com.lon.mcpgateway.gateway.types.validation.ValidationModels.ToolCallEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@Component
class OpenAiValidationChatbot implements OpenAiValidationChatbotPort {
    private static final String SYSTEM_PROMPT = "你是 MCP 网关验证台助手。只在必要时调用提供的 Tool；解释每次调用及结果，且不得编造 Tool 结果。";
    private final WebClient.Builder webClientBuilder;
    private final ValidationModelSettingsPort settings;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    OpenAiValidationChatbot(WebClient.Builder webClientBuilder, ValidationModelSettingsPort settings, ObjectMapper objectMapper,
            @Value("${OPENAI_API_KEY:}") String apiKey) {
        this.webClientBuilder = webClientBuilder;
        this.settings = settings;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    @Override
    public Flux<ChatbotEvent> respond(ChatbotRequest request) {
        if (apiKey.isBlank()) {
            return Flux.error(new GatewayException("VALIDATION_MODEL_UNAVAILABLE", "服务端未配置 OpenAI API Key"));
        }
        ValidationModelSettingsPort.ModelSettings modelSettings = settings.settings();
        Map<Integer, ToolCallAccumulator> calls = new LinkedHashMap<>();
        return webClientBuilder.baseUrl(trimTrailingSlash(modelSettings.baseUrl())).build().post().uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM)
                .headers(headers -> headers.setBearerAuth(apiKey)).bodyValue(payload(modelSettings.model(), request))
                .retrieve().bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {
                }).concatMap(event -> Flux.fromIterable(events(event.data(), calls)))
                .onErrorMap(exception -> exception instanceof GatewayException ? exception
                        : new GatewayException("VALIDATION_MODEL_UNAVAILABLE", "调用 OpenAI 验证模型失败"));
    }

    private ObjectNode payload(String model, ChatbotRequest request) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", model).put("stream", true).put("tool_choice", "auto");
        ArrayNode messages = payload.putArray("messages");
        message(messages, "system", SYSTEM_PROMPT);
        request.messages().forEach(item -> message(messages, item.role(), item.content()));
        if (!request.toolResults().isEmpty()) {
            message(messages, "system", "本轮已执行的 Tool 结果（必须据此继续）：" + write(request.toolResults()));
        }
        ArrayNode tools = payload.putArray("tools");
        request.tools().forEach(tool -> {
            ObjectNode function = tools.addObject().put("type", "function").putObject("function");
            function.put("name", tool.name()).put("description", tool.description());
            try {
                function.set("parameters", objectMapper.readTree(tool.inputSchema()));
            } catch (Exception exception) {
                throw new GatewayException("VALIDATION_MCP_FAILURE", "MCP Tool 输入 schema 无效");
            }
        });
        return payload;
    }

    private void message(ArrayNode messages, String role, String content) {
        messages.addObject().put("role", role).put("content", content);
    }

    private List<ChatbotEvent> events(String data, Map<Integer, ToolCallAccumulator> calls) {
        if (data == null || data.isBlank()) {
            return List.of();
        }
        if ("[DONE]".equals(data)) {
            return calls.values().stream().map(ToolCallAccumulator::toEvent).map(event -> (ChatbotEvent) event).toList();
        }
        try {
            JsonNode delta = objectMapper.readTree(data).path("choices").path(0).path("delta");
            List<ChatbotEvent> events = new ArrayList<>();
            if (delta.hasNonNull("content")) {
                events.add(new TextDelta(delta.path("content").asText()));
            }
            for (JsonNode fragment : delta.path("tool_calls")) {
                int index = fragment.path("index").asInt();
                ToolCallAccumulator call = calls.computeIfAbsent(index, ignored -> new ToolCallAccumulator());
                call.append(fragment);
            }
            return events;
        } catch (Exception exception) {
            throw new GatewayException("VALIDATION_MODEL_UNAVAILABLE", "OpenAI 流式响应格式无效");
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("无法序列化验证上下文", exception);
        }
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private final class ToolCallAccumulator {
        private String id;
        private String name;
        private final StringBuilder arguments = new StringBuilder();

        void append(JsonNode fragment) {
            if (fragment.hasNonNull("id")) {
                id = fragment.path("id").asText();
            }
            JsonNode function = fragment.path("function");
            if (function.hasNonNull("name")) {
                name = function.path("name").asText();
            }
            if (function.hasNonNull("arguments")) {
                arguments.append(function.path("arguments").asText());
            }
        }

        ToolCallEvent toEvent() {
            try {
                return new ToolCallEvent(new ToolCall(id, name, objectMapper.readTree(arguments.toString())));
            } catch (Exception exception) {
                throw new GatewayException("VALIDATION_MODEL_UNAVAILABLE", "OpenAI Tool 调用参数无效");
            }
        }
    }
}
