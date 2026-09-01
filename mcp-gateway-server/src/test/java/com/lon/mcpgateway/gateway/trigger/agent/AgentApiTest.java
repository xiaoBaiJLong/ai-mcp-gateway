package com.lon.mcpgateway.gateway.trigger.agent;

import com.lon.mcpgateway.gateway.app.McpGatewayApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = McpGatewayApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:agents;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always"
})
class AgentApiTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsAgentAndRevealsItsCredentialOnlyOnce() {
        JsonNode created = createAgent("客服智能体", "处理用户查询");
        String agentId = created.path("data").path("id").asText();
        org.junit.jupiter.api.Assertions.assertEquals("客服智能体", created.path("data").path("name").asText());
        org.junit.jupiter.api.Assertions.assertTrue(created.path("data").path("credential").path("apiKey").asText().length() >= 32);
        org.junit.jupiter.api.Assertions.assertTrue(created.path("data").path("credential").path("prefix").isTextual());
        org.junit.jupiter.api.Assertions.assertTrue(created.path("data").path("credential").path("enabled").asBoolean());

        webTestClient.get().uri("/api/v1/agents/{agentId}", agentId)
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.name").isEqualTo("客服智能体")
                .jsonPath("$.data.credentials[0].prefix").exists()
                .jsonPath("$.data.credentials[0].apiKey").doesNotExist()
                .jsonPath("$.data.toolSnapshot").isEmpty();
    }

    @Test
    void managesCredentialLifecycleAndReplacesPublishedToolSnapshot() throws Exception {
        String agentId = createAgent("订单智能体", null).path("data").path("id").asText();
        jdbcTemplate.update("insert into mcp_tools (id, name, name_hash, description, enabled, created_at) values (?, ?, ?, ?, ?, ?)",
                "tool-1", "users.read", "a".repeat(64), "读取用户", true, Instant.now());
        jdbcTemplate.update("insert into mcp_tools (id, name, name_hash, description, enabled, created_at) values (?, ?, ?, ?, ?, ?)",
                "tool-2", "users.search", "b".repeat(64), "搜索用户", true, Instant.now());

        webTestClient.patch().uri("/api/v1/agents/{agentId}/credential", agentId).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"enabled\":false}")
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.data.credentials[0].enabled").isEqualTo(false);

        webTestClient.post().uri("/api/v1/agents/{agentId}/credential/reset", agentId)
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.data.apiKey").value(value -> org.junit.jupiter.api.Assertions.assertTrue(value.toString().startsWith("mcp_")));

        webTestClient.put().uri("/api/v1/agents/{agentId}/tool-snapshot", agentId).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"toolIds\":[\"tool-1\",\"tool-1\",\"tool-2\"]}")
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.toolSnapshot.length()").isEqualTo(2)
                .jsonPath("$.data.toolSnapshot[0].id").isEqualTo("tool-1")
                .jsonPath("$.data.toolSnapshot[1].id").isEqualTo("tool-2");

        webTestClient.put().uri("/api/v1/agents/{agentId}/tool-snapshot", agentId).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"toolIds\":[\"tool-2\"]}")
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.data.toolSnapshot[0].id").isEqualTo("tool-2");

        String agent = webTestClient.get().uri("/api/v1/agents/{agentId}", agentId)
                .exchange().expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody();
        JsonNode result = objectMapper.readTree(agent).path("data");
        org.junit.jupiter.api.Assertions.assertEquals(2, result.path("credentials").size());
        org.junit.jupiter.api.Assertions.assertEquals(1, result.path("credentials").findValuesAsText("enabled").stream().filter(Boolean::parseBoolean).count());
        org.junit.jupiter.api.Assertions.assertFalse(result.path("credentials").get(0).has("apiKey"));
        org.junit.jupiter.api.Assertions.assertEquals(1, result.path("toolSnapshot").size());
    }

    private JsonNode createAgent(String name, String description) {
        String request = description == null ? "{\"name\":\"" + name + "\"}" : "{\"name\":\"" + name + "\",\"description\":\"" + description + "\"}";
        String body = webTestClient.post().uri("/api/v1/agents").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange().expectStatus().isCreated().expectBody(String.class).returnResult().getResponseBody();
        try {
            return objectMapper.readTree(body);
        } catch (Exception exception) {
            throw new AssertionError("无法读取创建智能体响应", exception);
        }
    }
}
