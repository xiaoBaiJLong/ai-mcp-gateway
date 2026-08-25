package com.xiaobaijlong.mcpgateway.management.tool;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class ToolManagementService {

    private static final Pattern SERVICE_ID = Pattern.compile("[a-z][a-z0-9-]{0,62}");
    private static final Pattern TOOL_ID = Pattern.compile("[a-z][a-z0-9_-]{0,89}");
    private static final Set<String> HTTP_METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");
    private static final String CONNECTIVITY_FAILED = "连接失败";

    private final ToolManagementRepository repository;
    private final HttpClient httpClient;

    public ToolManagementService(ToolManagementRepository repository) {
        this.repository = repository;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(500))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Transactional
    public StaticUpstreamView createUpstream(String serviceId, String displayName, String baseUrl, String actor) {
        String normalizedServiceId = serviceId.trim();
        URI uri = validBaseUri(baseUrl.trim());
        if (!SERVICE_ID.matcher(normalizedServiceId).matches()) {
            throw badRequest("INVALID_SERVICE_ID", "服务标识仅允许小写字母、数字和连字符，且必须以字母开头");
        }
        ConnectivityResult connectivity = checkConnectivity(uri);
        Instant now = Instant.now();
        try {
            long id = repository.createUpstream(
                    normalizedServiceId,
                    displayName.trim(),
                    uri.toString(),
                    connectivity.status(),
                    connectivity.error(),
                    actor,
                    now
            );
            return getUpstream(id);
        } catch (DuplicateKeyException exception) {
            throw conflict("UPSTREAM_ALREADY_EXISTS", "服务标识已登记");
        }
    }

    public List<StaticUpstreamView> getUpstreams() {
        return repository.findUpstreams().stream().map(this::toUpstreamView).toList();
    }

    public StaticUpstreamView getUpstream(long id) {
        return toUpstreamView(requireUpstream(id));
    }

    @Transactional
    public StaticUpstreamView checkUpstream(long id) {
        ToolManagementRepository.UpstreamRow upstream = requireUpstream(id);
        ConnectivityResult connectivity = checkConnectivity(URI.create(upstream.baseUrl()));
        repository.updateConnectivity(id, connectivity.status(), connectivity.error(), Instant.now());
        return getUpstream(id);
    }

    @Transactional
    public ToolDraftView createDraft(ToolManagementRepository.DraftValues values, String actor) {
        ToolManagementRepository.UpstreamRow upstream = requireRegisteredUpstream(values.upstreamId());
        validateStableName(values.toolName(), upstream.serviceId());
        try {
            long id = repository.createDraft(normalize(values), actor, Instant.now());
            return getDraft(id);
        } catch (DuplicateKeyException exception) {
            throw conflict("DRAFT_ALREADY_EXISTS", "该工具已有草稿");
        }
    }

    public List<ToolDraftView> getDrafts() {
        return repository.findDrafts().stream().map(this::toDraftView).toList();
    }

    public ToolDraftView getDraft(long id) {
        return toDraftView(requireDraft(id));
    }

    @Transactional
    public ToolDraftView updateDraft(long id, ToolManagementRepository.DraftValues values) {
        ToolManagementRepository.DraftRow existing = requireDraft(id);
        if (!existing.toolName().equals(values.toolName().trim())) {
            throw badRequest("TOOL_NAME_IMMUTABLE", "工具稳定名称创建后不能修改");
        }
        ToolManagementRepository.UpstreamRow upstream = requireRegisteredUpstream(values.upstreamId());
        validateStableName(values.toolName(), upstream.serviceId());
        repository.updateDraft(id, normalize(values), Instant.now());
        return getDraft(id);
    }

    @Transactional
    public ToolDraftView validateDraft(long id) {
        ToolManagementRepository.DraftRow draft = requireDraft(id);
        List<String> errors = validationErrors(draft);
        repository.updateValidation(
                id,
                errors.isEmpty() ? ValidationStatus.VALID : ValidationStatus.INVALID,
                errors,
                Instant.now()
        );
        return getDraft(id);
    }

    @Transactional
    public ToolVersionView publish(long draftId, String actor) {
        if (!repository.lockDraft(draftId)) {
            throw new ToolManagementException(HttpStatus.NOT_FOUND, "DRAFT_NOT_FOUND", "工具草稿不存在");
        }
        ToolManagementRepository.DraftRow draft = requireDraft(draftId);
        if (draft.validationStatus() == ValidationStatus.UNVALIDATED) {
            throw badRequest("DRAFT_NOT_VALIDATED", "草稿必须先通过校验才能发布");
        }
        if (draft.validationStatus() == ValidationStatus.INVALID) {
            throw badRequest("DRAFT_INVALID", "草稿缺少发布所需配置");
        }
        // 即使草稿标记为 VALID，发布前仍重新检查配置，避免绕过当前安全边界。
        List<String> errors = validationErrors(draft);
        if (!errors.isEmpty()) {
            repository.updateValidation(draftId, ValidationStatus.INVALID, errors, Instant.now());
            throw badRequest("DRAFT_INVALID", "草稿缺少发布所需配置");
        }
        if (draft.riskLevel() == RiskLevel.DESTRUCTIVE) {
            throw conflict("DESTRUCTIVE_TOOL_NOT_PUBLISHABLE", "第一阶段禁止发布破坏性工具");
        }

        try {
            // 锁与唯一约束共同保证并发发布不会生成两个相同版本号。
            repository.lockUpstream(draft.upstreamId());
            int versionNumber = repository.nextVersionNumber(draft.toolName());
            long versionId = repository.createVersion(draft, versionNumber, actor, Instant.now());
            repository.deleteDraft(draftId);
            return getVersion(versionId);
        } catch (DuplicateKeyException exception) {
            throw conflict("VERSION_CONFLICT", "工具版本并发冲突，请重新从已发布版本创建草稿");
        }
    }

    public List<ToolVersionView> getVersions(String toolName) {
        return repository.findVersions(toolName).stream().map(this::toVersionView).toList();
    }

    public ToolVersionView getVersion(long id) {
        return toVersionView(requireVersion(id));
    }

    @Transactional
    public ToolDraftView createDraftFromVersion(long versionId, String actor) {
        ToolManagementRepository.VersionRow version = requireVersion(versionId);
        ToolManagementRepository.DraftValues values = new ToolManagementRepository.DraftValues(
                version.toolName(), version.displayName(), version.riskLevel(), version.upstreamId(),
                version.httpMethod(), version.path(), version.requestConfig(), version.responseConfig()
        );
        return createDraft(values, actor);
    }

    private ToolManagementRepository.DraftValues normalize(ToolManagementRepository.DraftValues values) {
        return new ToolManagementRepository.DraftValues(
                values.toolName().trim(), values.displayName().trim(), values.riskLevel(), values.upstreamId(),
                normalized(values.httpMethod()).toUpperCase(Locale.ROOT), normalized(values.path()),
                normalized(values.requestConfig()), normalized(values.responseConfig())
        );
    }

    private List<String> validationErrors(ToolManagementRepository.DraftRow draft) {
        List<String> errors = new ArrayList<>();
        if (draft.httpMethod().isBlank()) {
            errors.add("HTTP 方法不能为空");
        } else if (!HTTP_METHODS.contains(draft.httpMethod())) {
            errors.add("HTTP 方法不受支持");
        }
        if (draft.path().isBlank()) {
            errors.add("目标路径不能为空");
        } else if (!draft.path().startsWith("/") || draft.path().startsWith("//")) {
            // 这里只保存相对路径，最终地址只能由登记上游和该路径组合，Agent 无法注入目标主机。
            errors.add("目标路径必须是以 / 开头的相对路径");
        }
        return errors;
    }

    private void validateStableName(String rawToolName, String serviceId) {
        String toolName = rawToolName.trim();
        String requiredPrefix = serviceId + ".";
        if (!toolName.startsWith(requiredPrefix)
                || !TOOL_ID.matcher(toolName.substring(requiredPrefix.length())).matches()) {
            throw badRequest("TOOL_NAME_UPSTREAM_MISMATCH", "工具稳定名称必须符合 <已登记上游 serviceId>.<toolId>");
        }
    }

    private URI validBaseUri(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl);
            String scheme = uri.getScheme();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || !uri.isAbsolute()
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
            return uri.normalize();
        } catch (IllegalArgumentException exception) {
            throw badRequest("INVALID_UPSTREAM_URL", "基础地址必须是合法的绝对 http/https 地址");
        }
    }

    private ConnectivityResult checkConnectivity(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(800))
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build();
        try {
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return new ConnectivityResult(ConnectivityStatus.CONNECTED, "");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new ConnectivityResult(ConnectivityStatus.FAILED, CONNECTIVITY_FAILED);
        } catch (IOException | RuntimeException exception) {
            return new ConnectivityResult(ConnectivityStatus.FAILED, CONNECTIVITY_FAILED);
        }
    }

    private ToolManagementRepository.UpstreamRow requireRegisteredUpstream(long id) {
        return repository.findUpstream(id).orElseThrow(() ->
                badRequest("UPSTREAM_NOT_REGISTERED", "只能引用已登记的静态 HTTP 上游")
        );
    }

    private ToolManagementRepository.UpstreamRow requireUpstream(long id) {
        return repository.findUpstream(id).orElseThrow(() ->
                new ToolManagementException(HttpStatus.NOT_FOUND, "UPSTREAM_NOT_FOUND", "静态 HTTP 上游不存在")
        );
    }

    private ToolManagementRepository.DraftRow requireDraft(long id) {
        return repository.findDraft(id).orElseThrow(() ->
                new ToolManagementException(HttpStatus.NOT_FOUND, "DRAFT_NOT_FOUND", "工具草稿不存在")
        );
    }

    private ToolManagementRepository.VersionRow requireVersion(long id) {
        return repository.findVersion(id).orElseThrow(() ->
                new ToolManagementException(HttpStatus.NOT_FOUND, "VERSION_NOT_FOUND", "工具版本不存在")
        );
    }

    private StaticUpstreamView toUpstreamView(ToolManagementRepository.UpstreamRow row) {
        return new StaticUpstreamView(
                row.id(), row.serviceId(), row.displayName(), row.baseUrl(), row.connectivityStatus(),
                row.connectivityError(), row.lastCheckedAt(), row.createdBy(), row.createdAt(), row.updatedAt()
        );
    }

    private ToolDraftView toDraftView(ToolManagementRepository.DraftRow row) {
        List<String> errors = row.validationError().isBlank()
                ? List.of()
                : Arrays.asList(row.validationError().split("\\|", -1));
        return new ToolDraftView(
                row.id(), row.toolName(), row.displayName(), row.riskLevel(), row.upstreamId(), row.serviceId(),
                row.httpMethod(), row.path(), row.requestConfig(), row.responseConfig(), row.validationStatus(),
                errors, row.createdBy(), row.createdAt(), row.updatedAt()
        );
    }

    private ToolVersionView toVersionView(ToolManagementRepository.VersionRow row) {
        return new ToolVersionView(
                row.id(), row.toolName(), row.versionNumber(), row.displayName(), row.riskLevel(), row.upstreamId(),
                row.serviceId(), row.httpMethod(), row.path(), row.requestConfig(), row.responseConfig(),
                row.versionNumber() == repository.maxVersionNumber(row.toolName()), row.publishedBy(), row.publishedAt()
        );
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim();
    }

    private ToolManagementException badRequest(String code, String message) {
        return new ToolManagementException(HttpStatus.BAD_REQUEST, code, message);
    }

    private ToolManagementException conflict(String code, String message) {
        return new ToolManagementException(HttpStatus.CONFLICT, code, message);
    }

    private record ConnectivityResult(ConnectivityStatus status, String error) { }
}
