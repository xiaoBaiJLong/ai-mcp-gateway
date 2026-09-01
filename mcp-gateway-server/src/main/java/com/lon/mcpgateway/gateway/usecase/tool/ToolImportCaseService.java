package com.lon.mcpgateway.gateway.usecase.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lon.mcpgateway.gateway.api.discovery.BusinessServiceDiscoveryPort;
import com.lon.mcpgateway.gateway.api.discovery.OpenApiDocumentPort;
import com.lon.mcpgateway.gateway.api.tool.McpToolRepositoryPort;
import com.lon.mcpgateway.gateway.api.tool.ToolImportCase;
import com.lon.mcpgateway.gateway.domain.tool.OpenApiImporter;
import com.lon.mcpgateway.gateway.types.common.GatewayException;
import com.lon.mcpgateway.gateway.types.tool.ToolModels.CreateToolRequest;
import com.lon.mcpgateway.gateway.types.tool.ToolModels.DraftRequest;
import com.lon.mcpgateway.gateway.types.tool.ToolModels.HttpMappingRecord;
import com.lon.mcpgateway.gateway.types.tool.ToolModels.MappingView;
import com.lon.mcpgateway.gateway.types.tool.ToolModels.MappingUpdateRequest;
import com.lon.mcpgateway.gateway.types.tool.ToolModels.McpToolRecord;
import com.lon.mcpgateway.gateway.types.tool.ToolModels.OpenApiOperationsView;
import com.lon.mcpgateway.gateway.types.tool.ToolModels.StoredToolView;
import com.lon.mcpgateway.gateway.types.tool.ToolModels.ToolDraftView;
import com.lon.mcpgateway.gateway.types.tool.ToolModels.ToolSourceView;
import com.lon.mcpgateway.gateway.types.tool.ToolModels.ToolStatusUpdateRequest;
import com.lon.mcpgateway.gateway.types.tool.ToolModels.ToolUpdateCheckView;
import com.lon.mcpgateway.gateway.types.tool.ToolModels.ToolView;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class ToolImportCaseService implements ToolImportCase {
    private final BusinessServiceDiscoveryPort discovery;
    private final OpenApiDocumentPort documentClient;
    private final OpenApiImporter importer;
    private final McpToolRepositoryPort toolRepository;
    private final ObjectMapper objectMapper;

    public ToolImportCaseService(BusinessServiceDiscoveryPort discovery, OpenApiDocumentPort documentClient, OpenApiImporter importer,
            McpToolRepositoryPort toolRepository, ObjectMapper objectMapper) {
        this.discovery = discovery;
        this.documentClient = documentClient;
        this.importer = importer;
        this.toolRepository = toolRepository;
        this.objectMapper = objectMapper;
    }

    public List<ToolSourceView> sources() {
        return discovery.findHealthyServiceNames().stream().sorted().map(ToolSourceView::new).toList();
    }

    public OpenApiOperationsView operations(String serviceName) {
        return new OpenApiOperationsView(serviceName, importer.operations(serviceName, documentClient.fetch(serviceName)));
    }

    public ToolDraftView draft(DraftRequest request) {
        return importer.draft(request.serviceName(), request.method(), request.path(), documentClient.fetch(request.serviceName()));
    }

    @Transactional
    public ToolView create(CreateToolRequest request) {
        JsonNode document = documentClient.fetch(request.serviceName());
        ToolDraftView draft = importer.draft(request.serviceName(), request.method(), request.path(), document);
        String nameHash = hash(request.name());
        String sourceHash = hash(draft.serviceName() + "\n" + draft.method() + "\n" + draft.path());
        if (toolRepository.countByNameHash(nameHash) > 0) {
            throw new GatewayException("TOOL_NAME_EXISTS", "MCP 工具名称已存在");
        }
        if (toolRepository.countBySourceHash(sourceHash) > 0) {
            throw new GatewayException("TOOL_SOURCE_EXISTS", "该业务服务、HTTP 方法和路径已创建 MCP 工具");
        }
        String description = request.description() == null ? draft.initialDescription() : request.description();
        String id = UUID.randomUUID().toString();
        Instant createdAt = Instant.now();
        toolRepository.save(new McpToolRecord(id, request.name(), nameHash, description, true, createdAt),
                new HttpMappingRecord(id, draft.serviceName(), draft.method(), draft.path(), sourceHash,
                        draft.inputSchema().toString(), importer.operation(draft.method(), draft.path(), document).toString()));
        return new ToolView(id, request.name(), description, true, createdAt,
                new MappingView(draft.serviceName(), draft.method(), draft.path(), draft.inputSchema()));
    }

    public List<ToolView> tools() {
        return toolRepository.findAll().stream().map(this::view).toList();
    }

    @Transactional
    public ToolView updateStatus(String toolId, ToolStatusUpdateRequest request) {
        requireTool(toolId);
        if (!toolRepository.updateEnabled(toolId, request.enabled())) {
            throw new GatewayException("TOOL_NOT_FOUND", "未找到 MCP 工具");
        }
        return view(requireTool(toolId));
    }

    public ToolUpdateCheckView checkForUpdate(String toolId) {
        StoredToolView tool = requireTool(toolId);
        JsonNode document = documentClient.fetch(tool.serviceName());
        JsonNode operation = importer.operation(tool.method(), tool.path(), document);
        if (!operation.isObject()) {
            return new ToolUpdateCheckView("SOURCE_OPERATION_NOT_FOUND", "来源方法或路径已变化；请新建工具、禁用旧工具并重新发布受影响智能体的工具快照", null, null);
        }
        ToolDraftView draft;
        try {
            draft = importer.draft(tool.serviceName(), tool.method(), tool.path(), document);
        } catch (GatewayException exception) {
            if ("OPERATION_UNSUPPORTED".equals(exception.code())) {
                return new ToolUpdateCheckView("SOURCE_OPERATION_UNSUPPORTED", exception.getMessage(), null, null);
            }
            throw exception;
        }
        if (operation.equals(readJson(tool.operationSnapshot()))) {
            return new ToolUpdateCheckView("UP_TO_DATE", "来源 OpenAPI operation 没有变化", null, null);
        }
        return new ToolUpdateCheckView("CHANGED", "检测到来源 OpenAPI operation 已变化，请预览并确认保存", draft, operation);
    }

    @Transactional
    public ToolView updateMapping(String toolId, MappingUpdateRequest request) {
        StoredToolView tool = requireTool(toolId);
        JsonNode document = documentClient.fetch(tool.serviceName());
        JsonNode operation = importer.operation(tool.method(), tool.path(), document);
        if (!operation.isObject()) {
            throw new GatewayException("OPERATION_NOT_FOUND", "来源 OpenAPI 中已找不到原 HTTP 方法和路径");
        }
        if (!operation.equals(readJson(request.operationSnapshot()))) {
            throw new GatewayException("STALE_TOOL_UPDATE_PREVIEW", "来源 OpenAPI 已在预览后变化，请重新检查并预览映射更新");
        }
        ToolDraftView draft = importer.draft(tool.serviceName(), tool.method(), tool.path(), document);
        if (!operation.equals(readJson(tool.operationSnapshot()))
                && !toolRepository.updateMapping(toolId, draft.inputSchema().toString(), operation.toString())) {
            throw new GatewayException("TOOL_NOT_FOUND", "未找到 MCP 工具");
        }
        return view(requireTool(toolId));
    }

    private StoredToolView requireTool(String toolId) {
        StoredToolView tool = toolRepository.findById(toolId);
        if (tool == null) {
            throw new GatewayException("TOOL_NOT_FOUND", "未找到 MCP 工具");
        }
        return tool;
    }

    private ToolView view(StoredToolView row) {
        return new ToolView(row.id(), row.name(), row.description(), row.enabled(), row.createdAt(),
                new MappingView(row.serviceName(), row.method(), row.path(), readJson(row.inputSchema())));
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            throw new IllegalStateException("无法读取已保存的 HTTP Mapping", exception);
        }
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte item : digest) {
                hex.append(String.format("%02x", item));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 缺少 SHA-256", exception);
        }
    }
}
