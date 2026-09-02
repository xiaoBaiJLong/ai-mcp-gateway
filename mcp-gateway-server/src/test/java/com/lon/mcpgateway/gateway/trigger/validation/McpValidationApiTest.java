package com.lon.mcpgateway.gateway.trigger.validation;

import com.lon.mcpgateway.gateway.api.validation.McpGatewayValidationClientPort;
import com.lon.mcpgateway.gateway.api.validation.McpValidationCase;
import com.lon.mcpgateway.gateway.api.validation.OpenAiValidationChatbotPort;
import com.lon.mcpgateway.gateway.app.McpGatewayApplication;
import com.lon.mcpgateway.gateway.types.validation.ValidationModels.ValidationTool;
import com.lon.mcpgateway.gateway.types.validation.ValidationModels.ChatRequest;
import com.lon.mcpgateway.gateway.types.validation.ValidationModels.ToolCall;
import com.lon.mcpgateway.gateway.types.validation.ValidationModels.ToolCallEvent;
import com.lon.mcpgateway.gateway.types.validation.ValidationModels.ToolResult;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import reactor.core.publisher.Flux;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = McpGatewayApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:validation-api;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always"
})
class McpValidationApiTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private McpGatewayValidationClientPort mcpClient;

    @MockBean
    private OpenAiValidationChatbotPort chatbot;

    @Autowired
    private McpValidationCase validation;

    @Test
    void verifiesAgentCredentialThroughMcpAndReturnsAvailableTools() {
        when(mcpClient.connect(eq("agent-key"))).thenReturn(List.of(
                new ValidationTool("users.get", "读取用户", "{\"type\":\"object\"}")));

        webTestClient.post().uri("/api/v1/validation/connect")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"agentKey\":\"agent-key\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo("OK")
                .jsonPath("$.data.tools[0].name").isEqualTo("users.get")
                .jsonPath("$.data.tools[0].description").isEqualTo("读取用户");
    }

    @Test
    void executesAtMostThreeToolsInOneConversation() {
        when(mcpClient.connect(eq("agent-key"))).thenReturn(List.of(new ValidationTool("users.get", "读取用户", "{\"type\":\"object\"}")));
        when(mcpClient.callTool(eq("agent-key"), any(), any())).thenReturn(new ToolResult(200, JsonNodeFactory.instance.objectNode(), null, false));
        when(chatbot.respond(any())).thenReturn(Flux.just(
                new ToolCallEvent(new ToolCall("call-1", "users.get", JsonNodeFactory.instance.objectNode())),
                new ToolCallEvent(new ToolCall("call-2", "users.get", JsonNodeFactory.instance.objectNode())),
                new ToolCallEvent(new ToolCall("call-3", "users.get", JsonNodeFactory.instance.objectNode())),
                new ToolCallEvent(new ToolCall("call-4", "users.get", JsonNodeFactory.instance.objectNode()))));

        List<String> eventTypes = validation.chat(new ChatRequest("agent-key", List.of())).map(event -> event.type()).collectList().block();

        org.junit.jupiter.api.Assertions.assertEquals(List.of("tool_call", "tool_status", "tool_result", "tool_call", "tool_status", "tool_result", "tool_call", "tool_status", "tool_result", "limit"), eventTypes);
        verify(mcpClient, org.mockito.Mockito.times(3)).callTool(eq("agent-key"), any(), any());
    }
}
