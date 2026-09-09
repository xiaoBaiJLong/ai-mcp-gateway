package com.lon.mcpgateway.gateway.infrastructure.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lon.mcpgateway.gateway.api.validation.ValidationModelSettingsPort;
import com.lon.mcpgateway.gateway.types.validation.ValidationModels.ChatbotRequest;
import com.lon.mcpgateway.gateway.types.validation.ValidationModels.ConversationMessage;
import com.lon.mcpgateway.gateway.types.validation.ValidationModels.ToolCallEvent;
import com.lon.mcpgateway.gateway.types.validation.ValidationModels.ValidationTool;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class OpenAiValidationChatbotTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void mapsMcpToolNamesToModelSafeAliasesAndBack() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = ("data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call-1\","
                    + "\"function\":{\"name\":\"mcp_tool_0\",\"arguments\":\"{\\\"path\\\":{\\\"userId\\\":\\\"u-1\\\"}}\"}}]}}]}\n\n"
                    + "data: [DONE]\n\n").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        ValidationModelSettingsPort settings = () -> new ValidationModelSettingsPort.ModelSettings(
                "deepseek-v4-flash", "http://127.0.0.1:" + server.getAddress().getPort());
        var chatbot = new OpenAiValidationChatbot(WebClient.builder(), settings, objectMapper, "test-key");
        var request = new ChatbotRequest(
                List.of(new ConversationMessage("user", "查询用户 u-1")),
                List.of(new ValidationTool("users.get", "按用户 ID 查询用户",
                        "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"object\"}}}")),
                List.of());

        var events = chatbot.respond(request).collectList().block();

        assertEquals("Bearer test-key", authorization.get());
        JsonNode payload = objectMapper.readTree(requestBody.get());
        assertEquals("mcp_tool_0", payload.path("tools").path(0).path("function").path("name").asText());
        assertTrue(payload.path("tools").path(0).path("function").path("description").asText().contains("users.get"));
        ToolCallEvent event = assertInstanceOf(ToolCallEvent.class, events.getFirst());
        assertEquals("users.get", event.toolCall().name());
        assertEquals("u-1", event.toolCall().arguments().path("path").path("userId").asText());
    }
}
