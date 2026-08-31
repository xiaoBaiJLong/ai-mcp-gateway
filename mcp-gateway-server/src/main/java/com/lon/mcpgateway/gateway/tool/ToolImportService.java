package com.lon.mcpgateway.gateway.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ToolImportService {
    private final BusinessServiceDiscovery discovery;
    private final OpenApiDocumentClient documentClient;
    private final OpenApiImporter importer;
    private final ToolMapper toolMapper;
    private final ObjectMapper objectMapper;

    ToolImportService(BusinessServiceDiscovery discovery, OpenApiDocumentClient documentClient, OpenApiImporter importer,
            ToolMapper toolMapper, ObjectMapper objectMapper) {
        this.discovery = discovery;
        this.documentClient = documentClient;
        this.importer = importer;
        this.toolMapper = toolMapper;
        this.objectMapper = objectMapper;
    }

    List<ToolSourceView> sources() {
        return discovery.findHealthyServiceNames().stream().sorted().map(ToolSourceView::new).toList();
    }

    OpenApiOperationsView operations(String serviceName) {
        return new OpenApiOperationsView(serviceName, importer.operations(serviceName, documentClient.fetch(serviceName)));
    }

    ToolDraftView draft(DraftRequest request) {
        return importer.draft(request.serviceName(), request.method(), request.path(), documentClient.fetch(request.serviceName()));
    }

    @Transactional
    ToolView create(CreateToolRequest request) {
        JsonNode document = documentClient.fetch(request.serviceName());
        ToolDraftView draft = importer.draft(request.serviceName(), request.method(), request.path(), document);
        String nameHash = hash(request.name());
        String sourceHash = hash(draft.serviceName() + "\n" + draft.method() + "\n" + draft.path());
        if (toolMapper.countByNameHash(nameHash) > 0) {
            throw new ApiException("TOOL_NAME_EXISTS", HttpStatus.CONFLICT, "MCP 工具名称已存在");
        }
        if (toolMapper.countBySourceHash(sourceHash) > 0) {
            throw new ApiException("TOOL_SOURCE_EXISTS", HttpStatus.CONFLICT, "该业务服务、HTTP 方法和路径已创建 MCP 工具");
        }
        String description = request.description() == null ? draft.initialDescription() : request.description();
        String id = UUID.randomUUID().toString();
        Instant createdAt = Instant.now();
        toolMapper.insertTool(new ToolMapper.StoredTool(id, request.name(), nameHash, description, true, createdAt));
        toolMapper.insertMapping(new ToolMapper.StoredMapping(id, draft.serviceName(), draft.method(), draft.path(), sourceHash,
                draft.inputSchema().toString(), importer.operation(draft.method(), draft.path(), document).toString()));
        return new ToolView(id, request.name(), description, true, createdAt,
                new MappingView(draft.serviceName(), draft.method(), draft.path(), draft.inputSchema()));
    }

    List<ToolView> tools() {
        return toolMapper.findAll().stream().map(row -> {
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
