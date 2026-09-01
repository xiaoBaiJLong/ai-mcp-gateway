package com.lon.mcpgateway.gateway.types.tool;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.List;

public final class ToolModels {
    private ToolModels() {
    }

    public record ToolSourceView(String name) {
    }

    public record OperationView(String serviceName, String method, String path, String operationId, String summary,
            String description, boolean deprecated, boolean supported, String unsupportedReason) {
    }

    public record OpenApiOperationsView(String serviceName, List<OperationView> operations) {
    }

    public record DraftRequest(@NotBlank String serviceName, @NotBlank String method, @NotBlank String path) {
    }

    public record ToolDraftView(String serviceName, String method, String path, String initialName, String initialDescription,
            JsonNode inputSchema) {
    }

    public record CreateToolRequest(@NotBlank String serviceName, @NotBlank String method, @NotBlank String path,
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_.-]{1,128}$", message = "名称只能包含 ASCII 字母、数字、下划线、连字符和点，长度为 1–128") String name,
            String description) {
    }

    public record MappingView(String serviceName, String method, String path, JsonNode inputSchema) {
    }

    public record ToolView(String id, String name, String description, boolean enabled, Instant createdAt, MappingView mapping) {
    }

    public record ToolStatusUpdateRequest(@NotNull Boolean enabled) {
    }

    public record ToolUpdateCheckView(String status, String message, ToolDraftView draft, JsonNode operationSnapshot) {
    }

    public record MappingUpdateRequest(@NotBlank String operationSnapshot) {
    }

    public record McpToolRecord(String id, String name, String nameHash, String description, boolean enabled, Instant createdAt) {
    }

    public record HttpMappingRecord(String toolId, String serviceName, String method, String path, String sourceHash,
            String inputSchema, String operationSnapshot) {
    }

    public record StoredToolView(String id, String name, String description, boolean enabled, Instant createdAt,
            String serviceName, String method, String path, String inputSchema, String operationSnapshot) {
    }

    public record RuntimeToolRecord(String id, String name, String description, String inputSchema,
            String serviceName, String method, String path) {
    }
}
