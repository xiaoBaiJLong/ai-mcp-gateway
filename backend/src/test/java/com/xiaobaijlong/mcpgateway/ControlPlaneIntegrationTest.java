package com.xiaobaijlong.mcpgateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ControlPlaneIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Flyway flyway;

    @Test
    void adminCanLoginAndUpdateGatewayProfile() throws Exception {
        MockHttpSession session = login("admin", "666666", "PLATFORM_ADMIN");

        mockMvc.perform(put("/api/management/gateway-profile")
                        .session(session)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"研发 MCP 网关\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("研发 MCP 网关"))
                .andExpect(jsonPath("$.updatedBy").value("admin"));
    }

    @Test
    void auditorCanLoginAndReadButCannotUpdateGatewayProfile() throws Exception {
        MockHttpSession adminSession = login("admin", "666666", "PLATFORM_ADMIN");
        mockMvc.perform(put("/api/management/gateway-profile")
                        .session(adminSession)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"越权前名称\"}"))
                .andExpect(status().isOk());

        MockHttpSession auditorSession = login("auditor", "666666", "AUDIT_VIEWER");
        mockMvc.perform(get("/api/management/gateway-profile").session(auditorSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("越权前名称"));

        mockMvc.perform(put("/api/management/gateway-profile")
                        .session(auditorSession)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"不应写入\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/management/gateway-profile").session(auditorSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("越权前名称"));
    }

    @Test
    void invalidCredentialsAreRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void flywayMigrationIsIdempotentAndHealthShowsApplicationDatabase() throws Exception {
        assertThat(flyway.migrate().migrationsExecuted).isZero();

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"));

        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.db.status").value("UP"));
    }

    private MockHttpSession login(String username, String password, String expectedRole) throws Exception {
        String body = objectMapper.writeValueAsString(new LoginPayload(username, password));
        var result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.role").value(expectedRole))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.path("csrfToken").asText()).isNotBlank();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private record LoginPayload(String username, String password) {
    }
}
