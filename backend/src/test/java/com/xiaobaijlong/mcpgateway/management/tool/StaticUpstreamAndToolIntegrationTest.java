package com.xiaobaijlong.mcpgateway.management.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaticUpstreamAndToolIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ToolManagementService service;

    @Test
    void registrationReturnsDistinctConnectedAndFailedResults() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        try {
            createUpstream("reachable", "可连通服务", "http://127.0.0.1:" + server.getAddress().getPort())
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.connectivityStatus").value("CONNECTED"))
                    .andExpect(jsonPath("$.connectivityError").value(""));
        } finally {
            server.stop(0);
        }

        createUpstream("unreachable", "不可连通服务", "http://127.0.0.1:1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.connectivityStatus").value("FAILED"))
                .andExpect(jsonPath("$.connectivityError").value("连接失败"));

        createUpstream("invalid-url", "非法地址", "file:///etc/passwd")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_UPSTREAM_URL"));
    }

    @Test
    void draftRejectsAnUnregisteredUpstreamAndAbsoluteTargetPath() throws Exception {
        createDraft("inventory.unregistered", "未登记上游", "READ_ONLY", 999999, "GET", "/inventory")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UPSTREAM_NOT_REGISTERED"));

        long upstreamId = upstreamId("registered-boundary", "http://127.0.0.1:1");
        createDraft("another-service.read", "错误命名空间", "READ_ONLY", upstreamId, "GET", "/inventory")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOOL_NAME_UPSTREAM_MISMATCH"));
        JsonNode draft = responseBody(createDraft(
                "registered-boundary.read", "读取边界", "READ_ONLY", upstreamId,
                "GET", "http://169.254.169.254/latest/meta-data"
        ).andExpect(status().isCreated()).andReturn());

        postAsAdmin("/api/management/tool-drafts/" + draft.path("id").asLong() + "/validate", "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validationStatus").value("INVALID"))
                .andExpect(jsonPath("$.validationErrors[0]").value("目标路径必须是以 / 开头的相对路径"));
    }

    @Test
    void draftWithMissingMinimumConfigurationCannotBePublished() throws Exception {
        long upstreamId = upstreamId("missing-config", "http://127.0.0.1:1");
        JsonNode draft = responseBody(createDraft(
                "missing-config.read", "缺少配置", "READ_ONLY", upstreamId, "", ""
        ).andExpect(status().isCreated())
                .andExpect(jsonPath("$.validationStatus").value("UNVALIDATED"))
                .andReturn());
        long draftId = draft.path("id").asLong();

        postAsAdmin("/api/management/tool-drafts/" + draftId + "/validate", "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validationStatus").value("INVALID"))
                .andExpect(jsonPath("$.validationErrors.length()").value(2));
        postAsAdmin("/api/management/tool-drafts/" + draftId + "/publish", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DRAFT_INVALID"));
        assertThat(versionCount("missing-config.read")).isZero();
    }

    @Test
    void completeDraftMustBeExplicitlyValidatedBeforePublishing() throws Exception {
        long upstreamId = upstreamId("needs-validation", "http://127.0.0.1:1");
        JsonNode draft = responseBody(createDraft(
                "needs-validation.read", "需要校验", "READ_ONLY", upstreamId, "GET", "/items"
        ).andExpect(status().isCreated()).andReturn());

        postAsAdmin("/api/management/tool-drafts/" + draft.path("id").asLong() + "/publish", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DRAFT_NOT_VALIDATED"));
        assertThat(versionCount("needs-validation.read")).isZero();
    }

    @Test
    void readonlyPublishingAppendsImmutableVersionsAndNewDraftDoesNotChangeCurrentVersion() throws Exception {
        long upstreamId = upstreamId("catalog", "http://127.0.0.1:1");
        JsonNode firstDraft = responseBody(createDraft(
                "catalog.read", "读取目录", "READ_ONLY", upstreamId, "GET", "/catalog"
        ).andExpect(status().isCreated()).andReturn());
        long firstDraftId = firstDraft.path("id").asLong();

        postAsAdmin("/api/management/tool-drafts/" + firstDraftId + "/validate", "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validationStatus").value("VALID"));
        JsonNode versionOne = responseBody(postAsAdmin(
                "/api/management/tool-drafts/" + firstDraftId + "/publish", "{}"
        ).andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNumber").value(1))
                .andExpect(jsonPath("$.current").value(true))
                .andReturn());
        long versionOneId = versionOne.path("id").asLong();

        putAsAdmin("/api/management/tool-versions/" + versionOneId, "{\"displayName\":\"禁止覆盖\"}")
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(patch("/api/management/tool-versions/{id}", versionOneId)
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/management/tool-versions/{id}", versionOneId)
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isMethodNotAllowed());
        JsonNode copiedDraft = responseBody(postAsAdmin(
                "/api/management/tool-versions/" + versionOneId + "/draft", "{}"
        ).andExpect(status().isCreated())
                .andExpect(jsonPath("$.displayName").value("读取目录"))
                .andReturn());
        long copiedDraftId = copiedDraft.path("id").asLong();

        getAsAuditor("/api/management/tool-versions/" + versionOneId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("读取目录"))
                .andExpect(jsonPath("$.current").value(true));

        putAsAdmin("/api/management/tool-drafts/" + copiedDraftId, """
                {"toolName":"catalog.read","displayName":"读取目录新版","riskLevel":"READ_ONLY",
                 "upstreamId":%d,"httpMethod":"GET","path":"/v2/catalog"}
                """.formatted(upstreamId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validationStatus").value("UNVALIDATED"));
        postAsAdmin("/api/management/tool-drafts/" + copiedDraftId + "/validate", "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validationStatus").value("VALID"));
        postAsAdmin("/api/management/tool-drafts/" + copiedDraftId + "/publish", "{}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNumber").value(2))
                .andExpect(jsonPath("$.displayName").value("读取目录新版"));

        getAsAuditor("/api/management/tool-versions?toolName=catalog.read")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].versionNumber").value(1))
                .andExpect(jsonPath("$[0].displayName").value("读取目录"))
                .andExpect(jsonPath("$[0].current").value(false))
                .andExpect(jsonPath("$[1].versionNumber").value(2))
                .andExpect(jsonPath("$[1].current").value(true));
        assertThat(versionCount("catalog.read")).isEqualTo(2);
    }

    @Test
    void destructiveDraftCanBeSavedAndValidatedButCannotBePublished() throws Exception {
        long upstreamId = upstreamId("dangerous", "http://127.0.0.1:1");
        JsonNode draft = responseBody(createDraft(
                "dangerous.delete", "删除库存", "DESTRUCTIVE", upstreamId, "DELETE", "/inventory"
        ).andExpect(status().isCreated()).andReturn());
        long draftId = draft.path("id").asLong();

        postAsAdmin("/api/management/tool-drafts/" + draftId + "/validate", "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validationStatus").value("VALID"));
        postAsAdmin("/api/management/tool-drafts/" + draftId + "/publish", "{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DESTRUCTIVE_TOOL_NOT_PUBLISHABLE"));
        assertThat(versionCount("dangerous.delete")).isZero();
    }

    @Test
    void concurrentPublishingOfTheSameDraftCreatesOnlyOneVersion() throws Exception {
        long upstreamId = upstreamId("concurrent", "http://127.0.0.1:1");
        long draftId = responseBody(createDraft(
                "concurrent.read", "并发读取", "READ_ONLY", upstreamId, "GET", "/items"
        ).andExpect(status().isCreated()).andReturn()).path("id").asLong();
        postAsAdmin("/api/management/tool-drafts/" + draftId + "/validate", "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validationStatus").value("VALID"));

        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<Object>> futures = List.of(
                    executor.submit(() -> publishAfter(start, draftId)),
                    executor.submit(() -> publishAfter(start, draftId))
            );
            start.countDown();
            long successes = 0;
            long rejected = 0;
            for (Future<Object> future : futures) {
                Object result = future.get();
                if (result instanceof ToolVersionView) successes++;
                if (result instanceof ToolManagementException exception
                        && exception.code().equals("DRAFT_NOT_FOUND")) rejected++;
            }
            assertThat(successes).isEqualTo(1);
            assertThat(rejected).isEqualTo(1);
        }
        assertThat(versionCount("concurrent.read")).isEqualTo(1);
    }

    @Test
    void auditorCanReadManagementStateButCannotModifyIt() throws Exception {
        long upstreamId = upstreamId("audited", "http://127.0.0.1:1");
        long draftId = responseBody(createDraft(
                "audited.read", "审计读取", "READ_ONLY", upstreamId, "GET", "/audited"
        ).andExpect(status().isCreated()).andReturn()).path("id").asLong();

        getAsAuditor("/api/management/upstreams").andExpect(status().isOk());
        getAsAuditor("/api/management/tool-drafts").andExpect(status().isOk());
        getAsAuditor("/api/management/tool-versions").andExpect(status().isOk());

        mockMvc.perform(post("/api/management/upstreams")
                        .with(user("auditor").roles("AUDITOR"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\":\"forbidden\",\"displayName\":\"禁止\",\"baseUrl\":\"http://127.0.0.1:1\"}"))
                .andExpect(status().isForbidden());
        postAsAuditor("/api/management/upstreams/" + upstreamId + "/check", "{}")
                .andExpect(status().isForbidden());
        postAsAuditor("/api/management/tool-drafts", """
                {"toolName":"audited.other","displayName":"禁止","riskLevel":"READ_ONLY",
                 "upstreamId":%d,"httpMethod":"GET","path":"/other"}
                """.formatted(upstreamId)).andExpect(status().isForbidden());
        putAsAuditor("/api/management/tool-drafts/" + draftId, "{}")
                .andExpect(status().isForbidden());
        postAsAuditor("/api/management/tool-drafts/" + draftId + "/validate", "{}")
                .andExpect(status().isForbidden());
        postAsAuditor("/api/management/tool-drafts/" + draftId + "/publish", "{}")
                .andExpect(status().isForbidden());
        getAsAuditor("/api/management/tool-drafts/" + draftId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validationStatus").value("UNVALIDATED"));

        postAsAdmin("/api/management/tool-drafts/" + draftId + "/validate", "{}");
        long versionId = responseBody(postAsAdmin(
                "/api/management/tool-drafts/" + draftId + "/publish", "{}"
        ).andExpect(status().isCreated()).andReturn()).path("id").asLong();
        postAsAuditor("/api/management/tool-versions/" + versionId + "/draft", "{}")
                .andExpect(status().isForbidden());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tool_drafts WHERE tool_name IN ('audited.read', 'audited.other')", Integer.class
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM static_http_upstreams WHERE service_id = 'forbidden'", Integer.class
        )).isZero();
    }

    private ResultActions createUpstream(String serviceId, String displayName, String baseUrl) throws Exception {
        return postAsAdmin("/api/management/upstreams", objectMapper.writeValueAsString(
                new UpstreamPayload(serviceId, displayName, baseUrl)
        ));
    }

    private long upstreamId(String serviceId, String baseUrl) throws Exception {
        return responseBody(createUpstream(serviceId, serviceId, baseUrl)
                .andExpect(status().isCreated()).andReturn()).path("id").asLong();
    }

    private ResultActions createDraft(
            String toolName, String displayName, String riskLevel, long upstreamId, String httpMethod, String path
    ) throws Exception {
        return postAsAdmin("/api/management/tool-drafts", objectMapper.writeValueAsString(
                new DraftPayload(toolName, displayName, riskLevel, upstreamId, httpMethod, path)
        ));
    }

    private ResultActions postAsAdmin(String path, String body) throws Exception {
        return mockMvc.perform(post(path).with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions putAsAdmin(String path, String body) throws Exception {
        return mockMvc.perform(put(path).with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions postAsAuditor(String path, String body) throws Exception {
        return mockMvc.perform(post(path).with(user("auditor").roles("AUDITOR")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions putAsAuditor(String path, String body) throws Exception {
        return mockMvc.perform(put(path).with(user("auditor").roles("AUDITOR")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions getAsAuditor(String path) throws Exception {
        return mockMvc.perform(get(path).with(user("auditor").roles("AUDITOR")));
    }

    private JsonNode responseBody(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private int versionCount(String toolName) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tool_versions WHERE tool_name = ?", Integer.class, toolName
        );
    }

    private Object publishAfter(CountDownLatch start, long draftId) {
        try {
            start.await();
            return service.publish(draftId, "concurrent-test");
        } catch (ToolManagementException exception) {
            return exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private record UpstreamPayload(String serviceId, String displayName, String baseUrl) { }
    private record DraftPayload(
            String toolName, String displayName, String riskLevel, long upstreamId, String httpMethod, String path
    ) { }
}
