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
import com.lon.mcpgateway.gateway.types.tool.ToolModels.McpToolRecord;
import com.lon.mcpgateway.gateway.types.tool.ToolModels.OpenApiOperationsView;
import com.lon.mcpgateway.gateway.types.tool.ToolModels.StoredToolView;
import com.lon.mcpgateway.gateway.types.tool.ToolModels.ToolDraftView;
import com.lon.mcpgateway.gateway.types.tool.ToolModels.ToolSourceView;
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
        return toolRepository.findAll().stream().map(row -> {
            try {
                return new ToolView(row.id(), row.name(), row.description(), row.enabled(), row.createdAt(),
                        new MappingView(row.serviceName(), row.method(), row.path(), objectMapper.readTree(row.inputSchema())));
            } catch (Exception exception) {
                throw new IllegalStateException("无法读取已保存的 HTTP Mapping", exception);
            }
        }).toList();
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
