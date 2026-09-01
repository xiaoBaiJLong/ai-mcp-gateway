package com.lon.mcpgateway.gateway.trigger.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lon.mcpgateway.gateway.app.McpGatewayApplication;
import com.lon.mcpgateway.gateway.api.discovery.BusinessServiceDiscoveryPort;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = McpGatewayApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:mcp-protocol;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always"
})
class McpProtocolApiTest {

    private static final String AGENT_KEY = "mcp_protocol_test_key";
    private static final String SECOND_AGENT_KEY = "mcp_protocol_second_test_key";
    private static final String THIRD_AGENT_KEY = "mcp_protocol_third_test_key";
    private static final String SDK_CLIENT_KEY = "mcp_sdk_client_test_key";
    private static final String INVALID_ARGUMENT_KEY = "mcp_invalid_argument_test_key";
    private static final String UNSUPPORTED_PROTOCOL_KEY = "mcp_unsupported_protocol_test_key";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @LocalServerPort
    private int port;

    @MockBean
    private BusinessServiceDiscoveryPort discovery;

    private HttpServer downstream;
    private final AtomicReference<String> receivedPath = new AtomicReference<>();
    private final AtomicReference<String> receivedUserId = new AtomicReference<>();
    private final AtomicReference<String> receivedTenantId = new AtomicReference<>();
    private final AtomicReference<String> receivedMethod = new AtomicReference<>();
    private final AtomicReference<String> receivedBusinessHeader = new AtomicReference<>();
    private final AtomicReference<String> receivedRequestBody = new AtomicReference<>();

    @BeforeEach
    void startDownstream() throws Exception {
        downstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        downstream.createContext("/api/users/u-1", exchange -> {
            receivedPath.set(exchange.getRequestURI().toString());
            receivedUserId.set(exchange.getRequestHeaders().getFirst("X-User-Id"));
            receivedTenantId.set(exchange.getRequestHeaders().getFirst("X-Tenant-Id"));
            byte[] response = "{\"id\":\"u-1\",\"name\":\"MCP 测试用户\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        downstream.createContext("/api/users/search", exchange -> {
            receivedMethod.set(exchange.getRequestMethod());
            receivedBusinessHeader.set(exchange.getRequestHeaders().getFirst("X-Search-Scope"));
            receivedRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"keyword\":\"MCP\",\"page\":2}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(201, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        downstream.createContext("/api/status/not-found", exchange -> json(exchange, 404, "{\"code\":\"NOT_FOUND\"}"));
        downstream.createContext("/api/status/server-error", exchange -> json(exchange, 500, "{\"code\":\"SERVER_ERROR\"}"));
        downstream.createContext("/api/empty", exchange -> {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        downstream.createContext("/api/plain", exchange -> {
            byte[] response = "not-json".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        downstream.createContext("/api/slow", exchange -> {
            try {
                Thread.sleep(16_000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        downstream.start();
        when(discovery.findHealthyInstances(anyString())).thenReturn(List.of(
                new BusinessServiceDiscoveryPort.ServiceAddress("127.0.0.1", downstream.getAddress().getPort(), false)));
    }

    @AfterEach
    void stopDownstream() {
        downstream.stop(0);
    }

    private void json(com.sun.net.httpserver.HttpExchange exchange, int status, String response) throws java.io.IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Test
    void initializedAgentListsOnlyEnabledToolsInItsSnapshot() throws Exception {
        Instant now = Instant.now();
        jdbcTemplate.update("insert into agents (id, name, description, current_credential_id, created_at) values (?, ?, ?, ?, ?)",
                "agent-1", "协议测试智能体", "", "credential-1", now);
        jdbcTemplate.update("insert into agent_credentials (id, agent_id, key_hash, key_prefix, created_at, enabled) values (?, ?, ?, ?, ?, ?)",
                "credential-1", "agent-1", hash(AGENT_KEY), "mcp_protocol", now, true);
        insertTool("tool-visible", "users.read", true, now);
        insertTool("tool-disabled", "users.disabled", false, now);
        insertTool("tool-unassigned", "users.unassigned", true, now);
        jdbcTemplate.update("insert into agent_tool_assignments (agent_id, tool_id) values (?, ?)", "agent-1", "tool-visible");
        jdbcTemplate.update("insert into agent_tool_assignments (agent_id, tool_id) values (?, ?)", "agent-1", "tool-disabled");

        JsonNode initialized = exchange("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test-client\",\"version\":\"1.0\"}}}");
        assertEquals("2025-11-25", initialized.path("result").path("protocolVersion").asText());
        assertTrue(initialized.path("result").path("capabilities").has("tools"));

        JsonNode listed = exchange("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}");
        JsonNode tools = listed.path("result").path("tools");
        assertEquals(1, tools.size());
        assertEquals("users.read", tools.get(0).path("name").asText());
        assertTrue(tools.get(0).has("inputSchema"));
        assertFalse(listed.has("error"));
    }

    @Test
    void rejectsUnassignedAndDisabledToolsWithTheSameInvalidParamsError() throws Exception {
        Instant now = Instant.now();
        jdbcTemplate.update("insert into agents (id, name, description, current_credential_id, created_at) values (?, ?, ?, ?, ?)",
                "agent-2", "授权测试智能体", "", "credential-2", now);
        jdbcTemplate.update("insert into agent_credentials (id, agent_id, key_hash, key_prefix, created_at, enabled) values (?, ?, ?, ?, ?, ?)",
                "credential-2", "agent-2", hash(SECOND_AGENT_KEY), "mcp_protocol", now, true);
        insertTool("tool-assigned-disabled", "orders.disabled", false, now);
        insertTool("tool-unassigned-enabled", "orders.unassigned", true, now);
        jdbcTemplate.update("insert into agent_tool_assignments (agent_id, tool_id) values (?, ?)", "agent-2", "tool-assigned-disabled");

        JsonNode disabled = exchange(SECOND_AGENT_KEY, "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"orders.disabled\",\"arguments\":{}}}");
        JsonNode unassigned = exchange(SECOND_AGENT_KEY, "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"orders.unassigned\",\"arguments\":{}}}");

        assertEquals(-32602, disabled.path("error").path("code").asInt());
        assertEquals(disabled.path("error").path("message").asText(), unassigned.path("error").path("message").asText());
        assertEquals(null, receivedPath.get());
    }

    @Test
    void authorizedToolCallMapsArgumentsAndForwardsUserContext() throws Exception {
        Instant now = Instant.now();
        jdbcTemplate.update("insert into agents (id, name, description, current_credential_id, created_at) values (?, ?, ?, ?, ?)",
                "agent-3", "调用测试智能体", "", "credential-3", now);
        jdbcTemplate.update("insert into agent_credentials (id, agent_id, key_hash, key_prefix, created_at, enabled) values (?, ?, ?, ?, ?, ?)",
                "credential-3", "agent-3", hash(THIRD_AGENT_KEY), "mcp_protocol", now, true);
        insertTool("tool-callable", "users.get", true, now,
                "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"object\",\"properties\":{\"userId\":{\"type\":\"string\"}},\"required\":[\"userId\"]},\"query\":{\"type\":\"object\",\"properties\":{\"verbose\":{\"type\":\"boolean\"}}}},\"required\":[\"path\"]}");
        jdbcTemplate.update("insert into agent_tool_assignments (agent_id, tool_id) values (?, ?)", "agent-3", "tool-callable");

        JsonNode result = exchange(THIRD_AGENT_KEY,
                "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\",\"params\":{\"name\":\"users.get\",\"arguments\":{\"path\":{\"userId\":\"u-1\"},\"query\":{\"verbose\":true}}}}",
                "user-7", "tenant-9");

        assertEquals(200, result.path("result").path("structuredContent").path("httpStatus").asInt());
        assertEquals("u-1", result.path("result").path("structuredContent").path("body").path("id").asText());
        assertFalse(result.path("result").path("isError").asBoolean());
        assertEquals("/api/users/u-1?verbose=true", receivedPath.get());
        assertEquals("user-7", receivedUserId.get());
        assertEquals("tenant-9", receivedTenantId.get());
    }

    @Test
    void authorizedToolCallMapsJsonPostAndBusinessHeaders() throws Exception {
        Instant now = Instant.now();
        insertAgent("agent-post", "credential-post", "POST_TEST_KEY", now);
        insertTool("tool-post", "users.search", true, now,
                "{\"type\":\"object\",\"properties\":{\"headers\":{\"type\":\"object\",\"properties\":{\"X-Search-Scope\":{\"type\":\"string\"}},\"required\":[\"X-Search-Scope\"]},\"body\":{\"type\":\"object\",\"properties\":{\"keyword\":{\"type\":\"string\"},\"page\":{\"type\":\"integer\"}},\"required\":[\"keyword\"]}},\"required\":[\"headers\",\"body\"]}",
                "POST", "/api/users/search", "mock-user-service");
        jdbcTemplate.update("insert into agent_tool_assignments (agent_id, tool_id) values (?, ?)", "agent-post", "tool-post");

        JsonNode result = exchange("POST_TEST_KEY",
                "{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"tools/call\",\"params\":{\"name\":\"users.search\",\"arguments\":{\"headers\":{\"X-Search-Scope\":\"tenant-users\"},\"body\":{\"keyword\":\"MCP\",\"page\":2}}}}");

        assertEquals(201, result.path("result").path("structuredContent").path("httpStatus").asInt());
        assertEquals("MCP", result.path("result").path("structuredContent").path("body").path("keyword").asText());
        assertFalse(result.path("result").path("isError").asBoolean());
        assertEquals("POST", receivedMethod.get());
        assertEquals("tenant-users", receivedBusinessHeader.get());
        assertEquals("{\"keyword\":\"MCP\",\"page\":2}", receivedRequestBody.get());
    }

    @Test
    void preservesBusinessFailuresAndConvertsEmptyAndNonJsonResponses() throws Exception {
        Instant now = Instant.now();
        insertAgent("agent-response", "credential-response", "RESPONSE_TEST_KEY", now);
        insertTool("tool-not-found", "users.not-found", true, now, "{\"type\":\"object\"}", "GET", "/api/status/not-found", "mock-user-service");
        insertTool("tool-server-error", "users.server-error", true, now, "{\"type\":\"object\"}", "GET", "/api/status/server-error", "mock-user-service");
        insertTool("tool-empty", "users.empty", true, now, "{\"type\":\"object\"}", "GET", "/api/empty", "mock-user-service");
        insertTool("tool-plain", "users.plain", true, now, "{\"type\":\"object\"}", "GET", "/api/plain", "mock-user-service");
        for (String toolId : List.of("tool-not-found", "tool-server-error", "tool-empty", "tool-plain")) {
            jdbcTemplate.update("insert into agent_tool_assignments (agent_id, tool_id) values (?, ?)", "agent-response", toolId);
        }

        JsonNode notFound = call("RESPONSE_TEST_KEY", "users.not-found", "{}");
        JsonNode serverError = call("RESPONSE_TEST_KEY", "users.server-error", "{}");
        JsonNode empty = call("RESPONSE_TEST_KEY", "users.empty", "{}");
        JsonNode plain = call("RESPONSE_TEST_KEY", "users.plain", "{}");

        assertResponse(notFound, 404, true, null);
        assertEquals("NOT_FOUND", notFound.path("result").path("structuredContent").path("body").path("code").asText());
        assertResponse(serverError, 500, true, null);
        assertEquals("SERVER_ERROR", serverError.path("result").path("structuredContent").path("body").path("code").asText());
        assertResponse(empty, 204, false, null);
        assertTrue(empty.path("result").path("structuredContent").path("body").isNull());
        assertResponse(plain, 200, true, "UNSUPPORTED_RESPONSE_MEDIA_TYPE");
        assertEquals("not-json", plain.path("result").path("structuredContent").path("body").asText());
    }

    @Test
    void resolvesHealthyInstancesForEveryCallAndConvertsAvailabilityFailures() throws Exception {
        Instant now = Instant.now();
        insertAgent("agent-runtime", "credential-runtime", "RUNTIME_TEST_KEY", now);
        insertTool("tool-rotating", "users.rotating", true, now, "{\"type\":\"object\"}", "GET", "/api/users/u-1", "runtime-service");
        insertTool("tool-unavailable", "users.unavailable", true, now, "{\"type\":\"object\"}", "GET", "/api/users/u-1", "unavailable-service");
        insertTool("tool-timeout", "users.timeout", true, now, "{\"type\":\"object\"}", "GET", "/api/slow", "timeout-service");
        for (String toolId : List.of("tool-rotating", "tool-unavailable", "tool-timeout")) {
            jdbcTemplate.update("insert into agent_tool_assignments (agent_id, tool_id) values (?, ?)", "agent-runtime", toolId);
        }
        int unreachablePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unreachablePort = socket.getLocalPort();
        }
        when(discovery.findHealthyInstances("runtime-service")).thenReturn(List.of(
                new BusinessServiceDiscoveryPort.ServiceAddress("127.0.0.1", downstream.getAddress().getPort(), false),
                new BusinessServiceDiscoveryPort.ServiceAddress("127.0.0.1", unreachablePort, false)));
        when(discovery.findHealthyInstances("unavailable-service")).thenReturn(List.of());
        when(discovery.findHealthyInstances("timeout-service")).thenReturn(List.of(
                new BusinessServiceDiscoveryPort.ServiceAddress("127.0.0.1", downstream.getAddress().getPort(), false)));

        JsonNode first = call("RUNTIME_TEST_KEY", "users.rotating", "{}");
        JsonNode second = call("RUNTIME_TEST_KEY", "users.rotating", "{}");
        JsonNode unavailable = call("RUNTIME_TEST_KEY", "users.unavailable", "{}");
        JsonNode timeout = call("RUNTIME_TEST_KEY", "users.timeout", "{}");

        assertResponse(first, 200, false, null);
        assertResponse(second, 502, true, "BAD_GATEWAY");
        assertResponse(unavailable, 503, true, "SERVICE_UNAVAILABLE");
        assertResponse(timeout, 504, true, "TIMEOUT");
        verify(discovery, times(2)).findHealthyInstances("runtime-service");
    }

    @Test
    void javaSdkClientInvokesGetAndJsonPostToolsThroughStreamableHttp() {
        Instant now = Instant.now();
        jdbcTemplate.update("insert into agents (id, name, description, current_credential_id, created_at) values (?, ?, ?, ?, ?)",
                "agent-4", "SDK 客户端测试智能体", "", "credential-4", now);
        jdbcTemplate.update("insert into agent_credentials (id, agent_id, key_hash, key_prefix, created_at, enabled) values (?, ?, ?, ?, ?, ?)",
                "credential-4", "agent-4", hash(SDK_CLIENT_KEY), "mcp_protocol", now, true);
        insertTool("tool-sdk-client", "sdk.read", true, now,
                "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"object\",\"properties\":{\"userId\":{\"type\":\"string\"}},\"required\":[\"userId\"]}},\"required\":[\"path\"]}");
        insertTool("tool-sdk-post", "sdk.search", true, now,
                "{\"type\":\"object\",\"properties\":{\"body\":{\"type\":\"object\",\"properties\":{\"keyword\":{\"type\":\"string\"}},\"required\":[\"keyword\"]}},\"required\":[\"body\"]}",
                "POST", "/api/users/search", "mock-user-service");
        jdbcTemplate.update("insert into agent_tool_assignments (agent_id, tool_id) values (?, ?)", "agent-4", "tool-sdk-client");
        jdbcTemplate.update("insert into agent_tool_assignments (agent_id, tool_id) values (?, ?)", "agent-4", "tool-sdk-post");

        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + port)
                .endpoint("/mcp")
                .requestBuilder(java.net.http.HttpRequest.newBuilder().header("X-MCP-Agent-Key", SDK_CLIENT_KEY))
                .build();
        McpSyncClient client = McpClient.sync(transport).build();

        assertEquals("2025-11-25", client.initialize().protocolVersion());
        assertEquals(List.of("sdk.read", "sdk.search"), client.listTools().tools().stream().map(tool -> tool.name()).toList());
        McpSchema.CallToolResult get = client.callTool(new McpSchema.CallToolRequest("sdk.read", Map.of("path", Map.of("userId", "u-1"))));
        McpSchema.CallToolResult post = client.callTool(new McpSchema.CallToolRequest("sdk.search", Map.of("body", Map.of("keyword", "MCP"))));

        assertFalse(get.isError());
        assertFalse(post.isError());
        Map<?, ?> getEnvelope = (Map<?, ?>) get.structuredContent();
        Map<?, ?> postEnvelope = (Map<?, ?>) post.structuredContent();
        assertEquals(200, ((Number) getEnvelope.get("httpStatus")).intValue());
        assertEquals("u-1", ((Map<?, ?>) getEnvelope.get("body")).get("id"));
        assertEquals(201, ((Number) postEnvelope.get("httpStatus")).intValue());
        assertEquals("MCP", ((Map<?, ?>) postEnvelope.get("body")).get("keyword"));
    }

    @Test
    void invalidCredentialCannotListTools() {
        webTestClient.post().uri("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-MCP-Agent-Key", "mcp_not_a_valid_key")
                .bodyValue("{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/list\",\"params\":{}}")
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void rejectsUnsupportedMcpProtocolVersions() throws Exception {
        Instant now = Instant.now();
        jdbcTemplate.update("insert into agents (id, name, description, current_credential_id, created_at) values (?, ?, ?, ?, ?)",
                "agent-6", "协议版本测试智能体", "", "credential-6", now);
        jdbcTemplate.update("insert into agent_credentials (id, agent_id, key_hash, key_prefix, created_at, enabled) values (?, ?, ?, ?, ?, ?)",
                "credential-6", "agent-6", hash(UNSUPPORTED_PROTOCOL_KEY), "mcp_protocol", now, true);

        webTestClient.post().uri("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-MCP-Agent-Key", UNSUPPORTED_PROTOCOL_KEY)
                .header("MCP-Protocol-Version", "2025-06-18")
                .bodyValue("{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},\"clientInfo\":{\"name\":\"old-client\",\"version\":\"1.0\"}}}")
                .exchange().expectStatus().isBadRequest()
                .expectHeader().valueEquals("MCP-Protocol-Version", "2025-11-25")
                .expectBody().jsonPath("$.error.code").isEqualTo(-32602);
    }

    @Test
    void invalidArgumentsAreRejectedBeforeCallingTheBusinessService() throws Exception {
        Instant now = Instant.now();
        jdbcTemplate.update("insert into agents (id, name, description, current_credential_id, created_at) values (?, ?, ?, ?, ?)",
                "agent-5", "参数校验测试智能体", "", "credential-5", now);
        jdbcTemplate.update("insert into agent_credentials (id, agent_id, key_hash, key_prefix, created_at, enabled) values (?, ?, ?, ?, ?, ?)",
                "credential-5", "agent-5", hash(INVALID_ARGUMENT_KEY), "mcp_protocol", now, true);
        insertTool("tool-input-validation", "users.input-validation", true, now,
                "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"object\",\"properties\":{\"userId\":{\"type\":\"string\"}},\"required\":[\"userId\"]}},\"required\":[\"path\"]}");
        jdbcTemplate.update("insert into agent_tool_assignments (agent_id, tool_id) values (?, ?)", "agent-5", "tool-input-validation");

        JsonNode result = exchange(INVALID_ARGUMENT_KEY,
                "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{\"name\":\"users.input-validation\",\"arguments\":{}}}");
        JsonNode typeMismatch = exchange(INVALID_ARGUMENT_KEY,
                "{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"tools/call\",\"params\":{\"name\":\"users.input-validation\",\"arguments\":{\"path\":{\"userId\":42}}}}");
        JsonNode unsupported = exchange(INVALID_ARGUMENT_KEY,
                "{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"tools/call\",\"params\":{\"name\":\"users.input-validation\",\"arguments\":{\"path\":{\"userId\":\"u-1\"},\"headers\":{\"X-Unmapped\":\"value\"}}}}");

        assertEquals(400, result.path("result").path("structuredContent").path("httpStatus").asInt());
        assertEquals("INVALID_ARGUMENT", result.path("result").path("structuredContent").path("gatewayError").asText());
        assertTrue(result.path("result").path("isError").asBoolean());
        assertResponse(typeMismatch, 400, true, "INVALID_ARGUMENT");
        assertResponse(unsupported, 400, true, "INVALID_ARGUMENT");
        assertEquals(null, receivedPath.get());
    }

    private void insertTool(String id, String name, boolean enabled, Instant createdAt) {
        insertTool(id, name, enabled, createdAt, "{\"type\":\"object\"}");
    }

    private void insertTool(String id, String name, boolean enabled, Instant createdAt, String inputSchema) {
        insertTool(id, name, enabled, createdAt, inputSchema, "GET", "/api/users/{userId}", "mock-user-service");
    }

    private void insertTool(String id, String name, boolean enabled, Instant createdAt, String inputSchema, String method, String path,
            String serviceName) {
        jdbcTemplate.update("insert into mcp_tools (id, name, name_hash, description, enabled, created_at) values (?, ?, ?, ?, ?, ?)",
                id, name, hash(name), name, enabled, createdAt);
        jdbcTemplate.update("insert into http_mappings (tool_id, service_name, http_method, normalized_path, source_hash, input_schema, operation_snapshot) values (?, ?, ?, ?, ?, ?, ?)",
                id, serviceName, method, path, hash(id), inputSchema, "{}");
    }

    private void insertAgent(String agentId, String credentialId, String key, Instant createdAt) {
        jdbcTemplate.update("insert into agents (id, name, description, current_credential_id, created_at) values (?, ?, ?, ?, ?)",
                agentId, agentId, "", credentialId, createdAt);
        jdbcTemplate.update("insert into agent_credentials (id, agent_id, key_hash, key_prefix, created_at, enabled) values (?, ?, ?, ?, ?, ?)",
                credentialId, agentId, hash(key), "mcp_protocol", createdAt, true);
    }

    private JsonNode call(String agentKey, String toolName, String arguments) throws Exception {
        return exchange(agentKey, "{\"jsonrpc\":\"2.0\",\"id\":10,\"method\":\"tools/call\",\"params\":{\"name\":\"" + toolName
                + "\",\"arguments\":" + arguments + "}}");
    }

    private void assertResponse(JsonNode result, int httpStatus, boolean error, String gatewayError) {
        assertEquals(httpStatus, result.path("result").path("structuredContent").path("httpStatus").asInt());
        assertEquals(error, result.path("result").path("isError").asBoolean());
        if (gatewayError == null) {
            assertFalse(result.path("result").path("structuredContent").has("gatewayError"));
        } else {
            assertEquals(gatewayError, result.path("result").path("structuredContent").path("gatewayError").asText());
        }
    }

    private JsonNode exchange(String request) throws Exception {
        return exchange(AGENT_KEY, request);
    }

    private JsonNode exchange(String agentKey, String request) throws Exception {
        return exchange(agentKey, request, null, null);
    }

    private JsonNode exchange(String agentKey, String request, String userId, String tenantId) throws Exception {
        WebTestClient.RequestBodySpec requestSpec = webTestClient.post().uri("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .header("MCP-Protocol-Version", "2025-11-25")
                .header("X-MCP-Agent-Key", agentKey);
        if (userId != null) {
            requestSpec.header("X-User-Id", userId);
        }
        if (tenantId != null) {
            requestSpec.header("X-Tenant-Id", tenantId);
        }
        String response = requestSpec.bodyValue(request)
                .exchange().expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody();
        return objectMapper.readTree(response);
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte item : digest) {
                hex.append(String.format("%02x", item));
            }
            return hex.toString();
        } catch (Exception exception) {
            throw new AssertionError("无法计算测试哈希", exception);
        }
    }
}
