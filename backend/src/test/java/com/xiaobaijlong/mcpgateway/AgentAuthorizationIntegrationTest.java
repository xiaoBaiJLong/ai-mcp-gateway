package com.xiaobaijlong.mcpgateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaobaijlong.mcpgateway.management.authorization.AgentApiKeyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class AgentAuthorizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AgentApiKeyService apiKeyService;

    @Test
    void apiKeyIsShownOnceStoredAsDigestAndNeverReturnedByLaterReads(CapturedOutput output) throws Exception {
        MvcResult created = postAs("admin", "/api/management/agents", "{\"name\":\"key-once-agent\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.apiKey").isNotEmpty())
                .andReturn();

        JsonNode body = objectMapper.readTree(created.getResponse().getContentAsString());
        long agentId = body.path("id").asLong();
        String apiKey = body.path("apiKey").asText();
        assertThat(apiKey).hasSizeGreaterThanOrEqualTo(43);

        byte[] storedDigest = jdbcTemplate.queryForObject(
                "SELECT api_key_digest FROM agents WHERE id = ?",
                byte[].class,
                agentId
        );
        assertThat(storedDigest).hasSize(32);
        assertThat(storedDigest).isNotEqualTo(apiKey.getBytes(StandardCharsets.UTF_8));

        getAs("admin", "/api/management/agents/" + agentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKey").doesNotExist())
                .andExpect(jsonPath("$.apiKeyDigest").doesNotExist());

        getAs("admin", "/api/management/agents")
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .doesNotContain(apiKey));
        assertThat(output.getAll()).doesNotContain(apiKey);
    }

    @Test
    void wrongKeyFailsAndResetImmediatelyInvalidatesOldKey() throws Exception {
        JsonNode created = createAgent("reset-key-agent");
        long agentId = created.path("id").asLong();
        String oldKey = created.path("apiKey").asText();

        assertThat(apiKeyService.authenticate("mgw_wrong-key")).isEmpty();
        assertThat(apiKeyService.authenticate(oldKey)).isPresent();

        MvcResult reset = postAs("admin", "/api/management/agents/" + agentId + "/reset-api-key", "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKey").isNotEmpty())
                .andReturn();
        String newKey = responseBody(reset).path("apiKey").asText();

        assertThat(newKey).isNotEqualTo(oldKey);
        assertThat(apiKeyService.authenticate(oldKey)).isEmpty();
        assertThat(apiKeyService.authenticate(newKey)).isPresent();
        getAs("admin", "/api/management/agents/" + agentId)
                .andExpect(jsonPath("$.apiKey").doesNotExist());
    }

    @Test
    void duplicateAgentNameReturnsConflictWithoutReplacingTheExistingCredential() throws Exception {
        JsonNode first = createAgent("unique-agent");
        String firstKey = first.path("apiKey").asText();

        postAs("admin", "/api/management/agents", "{\"name\":\"unique-agent\"}")
                .andExpect(status().isConflict());

        assertThat(apiKeyService.authenticate(firstKey)).isPresent();
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agents WHERE name = ?",
                Integer.class,
                "unique-agent"
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    void agentWithoutRolesIsDeniedByDefault() throws Exception {
        long agentId = createAgent("default-deny-agent").path("id").asLong();

        getAs("admin", "/api/management/agents/" + agentId + "/permissions")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toolNames").isEmpty());
    }

    @Test
    void permissionsAreTheUnionOfAllAssignedRoles() throws Exception {
        long agentId = createAgent("union-agent").path("id").asLong();
        long roleOne = createRole("union-role-one").path("id").asLong();
        long roleTwo = createRole("union-role-two").path("id").asLong();
        long setOne = createToolSet("union-set-one", "crm.get_user", "crm.list_users").path("id").asLong();
        long setTwo = createToolSet("union-set-two", "crm.list_users", "orders.get_order").path("id").asLong();

        postAs("admin", "/api/management/roles/" + roleOne + "/tool-sets/" + setOne, "{}").andExpect(status().isNoContent());
        postAs("admin", "/api/management/roles/" + roleTwo + "/tool-sets/" + setTwo, "{}").andExpect(status().isNoContent());
        postAs("admin", "/api/management/agents/" + agentId + "/roles/" + roleOne, "{}").andExpect(status().isNoContent());
        postAs("admin", "/api/management/agents/" + agentId + "/roles/" + roleTwo, "{}").andExpect(status().isNoContent());

        getAs("admin", "/api/management/agents/" + agentId + "/permissions")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toolNames.length()").value(3))
                .andExpect(jsonPath("$.toolNames[0]").value("crm.get_user"))
                .andExpect(jsonPath("$.toolNames[1]").value("crm.list_users"))
                .andExpect(jsonPath("$.toolNames[2]").value("orders.get_order"));
    }

    @Test
    void revokingRoleRemovesPermissionsAndToolSetKeepsOnlyExplicitMembers() throws Exception {
        long agentId = createAgent("revocation-agent").path("id").asLong();
        long roleId = createRole("revocation-role").path("id").asLong();
        long toolSetId = createToolSet("stable-member-set", "crm.get_user").path("id").asLong();

        postAs("admin", "/api/management/roles/" + roleId + "/tool-sets/" + toolSetId, "{}")
                .andExpect(status().isNoContent());
        postAs("admin", "/api/management/agents/" + agentId + "/roles/" + roleId, "{}")
                .andExpect(status().isNoContent());

        getAs("admin", "/api/management/tool-sets/" + toolSetId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toolNames.length()").value(1))
                .andExpect(jsonPath("$.toolNames[0]").value("crm.get_user"));

        deleteAs("admin", "/api/management/roles/" + roleId + "/tool-sets/" + toolSetId)
                .andExpect(status().isNoContent());
        getAs("admin", "/api/management/agents/" + agentId + "/permissions")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toolNames").isEmpty());

        postAs("admin", "/api/management/roles/" + roleId + "/tool-sets/" + toolSetId, "{}")
                .andExpect(status().isNoContent());
        deleteAs("admin", "/api/management/agents/" + agentId + "/roles/" + roleId)
                .andExpect(status().isNoContent());
        getAs("admin", "/api/management/agents/" + agentId + "/permissions")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toolNames").isEmpty());
    }

    @Test
    void auditorCannotWriteAndRejectedRequestDoesNotChangeData() throws Exception {
        long agentId = createAgent("auditor-protected-agent").path("id").asLong();

        postAs("auditor", "/api/management/agents", "{\"name\":\"forbidden-agent\"}")
                .andExpect(status().isForbidden());
        putAs("auditor", "/api/management/agents/" + agentId, "{\"name\":\"forbidden-update\"}")
                .andExpect(status().isForbidden());
        deleteAs("auditor", "/api/management/agents/" + agentId)
                .andExpect(status().isForbidden());

        getAs("auditor", "/api/management/agents")
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains("auditor-protected-agent")
                        .doesNotContain("forbidden-agent", "forbidden-update"));
    }

    private JsonNode createAgent(String name) throws Exception {
        return responseBody(postAs("admin", "/api/management/agents", "{\"name\":\"" + name + "\"}")
                .andExpect(status().isCreated()).andReturn());
    }

    private JsonNode createRole(String name) throws Exception {
        return responseBody(postAs("admin", "/api/management/roles", "{\"name\":\"" + name + "\",\"description\":\"\"}")
                .andExpect(status().isCreated()).andReturn());
    }

    private JsonNode createToolSet(String name, String... toolNames) throws Exception {
        String members = objectMapper.writeValueAsString(toolNames);
        return responseBody(postAs("admin", "/api/management/tool-sets",
                        "{\"name\":\"" + name + "\",\"description\":\"\",\"toolNames\":" + members + "}")
                .andExpect(status().isCreated()).andReturn());
    }

    private org.springframework.test.web.servlet.ResultActions postAs(String username, String path, String body)
            throws Exception {
        return mockMvc.perform(post(path)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                        .user(username).roles(username.equals("admin") ? "ADMIN" : "AUDITOR"))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private org.springframework.test.web.servlet.ResultActions getAs(String username, String path) throws Exception {
        return mockMvc.perform(get(path)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                        .user(username).roles(username.equals("admin") ? "ADMIN" : "AUDITOR")));
    }

    private org.springframework.test.web.servlet.ResultActions putAs(String username, String path, String body)
            throws Exception {
        return mockMvc.perform(put(path)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                        .user(username).roles(username.equals("admin") ? "ADMIN" : "AUDITOR"))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private org.springframework.test.web.servlet.ResultActions deleteAs(String username, String path) throws Exception {
        return mockMvc.perform(delete(path)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                        .user(username).roles(username.equals("admin") ? "ADMIN" : "AUDITOR"))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()));
    }

    private JsonNode responseBody(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
