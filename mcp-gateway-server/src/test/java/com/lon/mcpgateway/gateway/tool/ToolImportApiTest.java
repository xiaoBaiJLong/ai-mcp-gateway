package com.lon.mcpgateway.gateway.tool;

import static org.mockito.BDDMockito.given;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lon.mcpgateway.gateway.McpGatewayApplication;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.mockito.Mockito.mock;

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
    private BusinessServiceDiscovery businessServiceDiscovery;

    @Autowired
    private OpenApiDocumentClient openApiDocumentClient;

    @TestConfiguration
    static class TestDoubles {
        @Bean
        @Primary
        BusinessServiceDiscovery testBusinessServiceDiscovery() {
            return mock(BusinessServiceDiscovery.class);
        }

        @Bean
        @Primary
        OpenApiDocumentClient testOpenApiDocumentClient() {
            return mock(OpenApiDocumentClient.class);
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
}
