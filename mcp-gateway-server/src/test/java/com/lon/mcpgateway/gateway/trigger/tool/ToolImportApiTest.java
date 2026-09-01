package com.lon.mcpgateway.gateway.trigger.tool;

import static org.mockito.BDDMockito.given;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lon.mcpgateway.gateway.api.discovery.BusinessServiceDiscoveryPort;
import com.lon.mcpgateway.gateway.api.discovery.OpenApiDocumentPort;
import com.lon.mcpgateway.gateway.app.McpGatewayApplication;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.reactive.server.EntityExchangeResult;

import static org.mockito.Mockito.mock;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = {McpGatewayApplication.class, ToolImportApiTest.TestDoubles.class}, properties = {
        "spring.datasource.url=jdbc:h2:mem:tool-import;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always"
})
class ToolImportApiTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BusinessServiceDiscoveryPort businessServiceDiscovery;

    @Autowired
    private OpenApiDocumentPort openApiDocumentClient;

    @TestConfiguration
    static class TestDoubles {
        @Bean
        @Primary
        BusinessServiceDiscoveryPort testBusinessServiceDiscovery() {
            return mock(BusinessServiceDiscoveryPort.class);
        }

        @Bean
        @Primary
        OpenApiDocumentPort testOpenApiDocumentClient() {
            return mock(OpenApiDocumentPort.class);
        }
    }

    @Test
    void importsOperationPreviewsDraftAndPersistsEnabledTool() throws Exception {
        given(businessServiceDiscovery.findHealthyServiceNames()).willReturn(List.of("mock-user-service"));
        given(openApiDocumentClient.fetch("mock-user-service")).willReturn(objectMapper.readTree("""
                {"openapi":"3.0.3","paths":{"/api/users/{userId}":{"get":{"operationId":"getUser","summary":"按用户 ID 查询用户","parameters":[{"name":"userId","in":"path","required":true,"schema":{"type":"string"}},{"name":"verbose","in":"query","schema":{"type":"boolean"}}],"responses":{"200":{"description":"ok","content":{"application/json":{"schema":{"type":"object"}}}}}}}}}
                """));

        webTestClient.get().uri("/api/v1/tool-sources")
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.data[0].name").isEqualTo("mock-user-service");

        String draftRequest = "{\"serviceName\":\"mock-user-service\",\"method\":\"GET\",\"path\":\"/api/users/{userId}\"}";
        webTestClient.post().uri("/api/v1/tool-drafts").contentType(MediaType.APPLICATION_JSON).bodyValue(draftRequest)
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.initialName").isEqualTo("mock-user-service.getUser")
                .jsonPath("$.data.inputSchema.properties.path.required[0]").isEqualTo("userId");

        webTestClient.post().uri("/api/v1/tools").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"serviceName\":\"mock-user-service\",\"method\":\"GET\",\"path\":\"/api/users/{userId}\",\"name\":\"mock-user-service.getUser\",\"description\":\"查询一个用户\"}")
                .exchange().expectStatus().isCreated()
                .expectBody().jsonPath("$.data.enabled").isEqualTo(true);

        webTestClient.get().uri("/api/v1/tools")
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].name").isEqualTo("mock-user-service.getUser")
                .jsonPath("$.data[0].mapping.serviceName").isEqualTo("mock-user-service")
                .jsonPath("$.data[0].mapping.path").isEqualTo("/api/users/{userId}");

        webTestClient.post().uri("/api/v1/tools").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"serviceName\":\"mock-user-service\",\"method\":\"GET\",\"path\":\"/api/users/{userId}\",\"name\":\"another-name\"}")
                .exchange().expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo("TOOL_SOURCE_EXISTS");
    }

    @Test
    void resolvesLocalPathItemParameterAndRequestBodyReferences() throws Exception {
        given(openApiDocumentClient.fetch("mock-user-service")).willReturn(objectMapper.readTree("""
                {"openapi":"3.0.3","paths":{"/api/users/{userId}":{"$ref":"#/components/pathItems/User"}},"components":{"pathItems":{"User":{"post":{"parameters":[{"$ref":"#/components/parameters/UserId"}],"requestBody":{"$ref":"#/components/requestBodies/Search"},"responses":{"200":{"description":"ok"}}}}},"parameters":{"UserId":{"name":"userId","in":"path","required":true,"schema":{"type":"string"}}},"requestBodies":{"Search":{"required":true,"content":{"application/json":{"schema":{"type":"object","properties":{"keyword":{"type":"string"}}}}}}}}}
                """));

        webTestClient.get().uri("/api/v1/tool-sources/mock-user-service/operations")
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.data.operations[0].supported").isEqualTo(true);

        webTestClient.post().uri("/api/v1/tool-drafts").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"serviceName\":\"mock-user-service\",\"method\":\"POST\",\"path\":\"/api/users/{userId}\"}")
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.inputSchema.properties.path.required[0]").isEqualTo("userId")
                .jsonPath("$.data.inputSchema.required").value(value -> value.toString().contains("body"));
    }

    @Test
    void wrapsInvalidToolNameInTheManagementApiEnvelope() {
        webTestClient.post().uri("/api/v1/tools").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"serviceName\":\"mock-user-service\",\"method\":\"GET\",\"path\":\"/users\",\"name\":\"contains space\"}")
                .exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_ARGUMENT");
    }

    @Test
    void managesToolStatusAndOnlyUpdatesMappingAfterExplicitConfirmation() throws Exception {
        given(openApiDocumentClient.fetch("issue-25-status-update-service")).willReturn(objectMapper.readTree("""
                {"openapi":"3.0.3","paths":{"/api/users/{userId}":{"get":{"operationId":"getUser","summary":"查询用户","parameters":[{"name":"userId","in":"path","required":true,"schema":{"type":"string"}}],"responses":{"200":{"description":"ok","content":{"application/json":{"schema":{"type":"object"}}}}}}}}}
                """));
        EntityExchangeResult<String> created = webTestClient.post().uri("/api/v1/tools").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"serviceName\":\"issue-25-status-update-service\",\"method\":\"GET\",\"path\":\"/api/users/{userId}\",\"name\":\"issue25.users.get\",\"description\":\"管理员说明\"}")
                .exchange().expectStatus().isCreated().expectBody(String.class).returnResult();
        String toolId = objectMapper.readTree(created.getResponseBody()).path("data").path("id").asText();

        webTestClient.patch().uri("/api/v1/tools/{toolId}/status", toolId).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"enabled\":false}").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.data.enabled").isEqualTo(false);

        given(openApiDocumentClient.fetch("issue-25-status-update-service")).willReturn(objectMapper.readTree("""
                {"openapi":"3.0.3","paths":{"/api/users/{userId}":{"get":{"operationId":"getUserV2","summary":"查询用户 v2","parameters":[{"name":"userId","in":"path","required":true,"schema":{"type":"string"}},{"name":"verbose","in":"query","schema":{"type":"boolean"}}],"responses":{"200":{"description":"ok","content":{"application/json":{"schema":{"type":"object"}}}}}}}}}
                """));

        EntityExchangeResult<String> checked = webTestClient.post().uri("/api/v1/tools/{toolId}/update-check", toolId).exchange()
                .expectStatus().isOk().expectBody(String.class).returnResult();
        JsonNode checkData = objectMapper.readTree(checked.getResponseBody()).path("data");
        assertEquals("CHANGED", checkData.path("status").asText());
        assertEquals("boolean", checkData.path("draft").path("inputSchema").path("properties").path("query")
                .path("properties").path("verbose").path("type").asText());
        String schemaAfterCheck = jdbcTemplate.queryForObject("select input_schema from http_mappings where tool_id = ?", String.class, toolId);
        assertFalse(objectMapper.readTree(schemaAfterCheck).path("properties").has("query"));

        webTestClient.put().uri("/api/v1/tools/{toolId}/mapping", toolId).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(objectMapper.writeValueAsString(Map.of("operationSnapshot", checkData.path("operationSnapshot").toString())))
                .exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.data.enabled").isEqualTo(false)
                .jsonPath("$.data.name").isEqualTo("issue25.users.get")
                .jsonPath("$.data.description").isEqualTo("管理员说明")
                .jsonPath("$.data.mapping.inputSchema.properties.query.properties.verbose.type").isEqualTo("boolean");

        given(openApiDocumentClient.fetch("issue-25-status-update-service")).willReturn(objectMapper.readTree("""
                {"openapi":"3.0.3","paths":{"/api/users/new":{"get":{"responses":{"200":{"description":"ok"}}}}}}
                """));
        webTestClient.post().uri("/api/v1/tools/{toolId}/update-check", toolId).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.data.status").isEqualTo("SOURCE_OPERATION_NOT_FOUND");
    }
}
