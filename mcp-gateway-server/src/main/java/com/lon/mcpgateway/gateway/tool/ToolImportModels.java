package com.lon.mcpgateway.gateway.tool;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.List;

record ToolSourceView(String name) {
}

record OperationView(String serviceName, String method, String path, String operationId, String summary,
        String description, boolean deprecated, boolean supported, String unsupportedReason) {
}

record OpenApiOperationsView(String serviceName, List<OperationView> operations) {
}

record DraftRequest(@NotBlank String serviceName, @NotBlank String method, @NotBlank String path) {
}

record ToolDraftView(String serviceName, String method, String path, String initialName, String initialDescription,
        JsonNode inputSchema) {
}

record CreateToolRequest(@NotBlank String serviceName, @NotBlank String method, @NotBlank String path,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_.-]{1,128}$", message = "名称只能包含 ASCII 字母、数字、下划线、连字符和点，长度为 1–128") String name,
        String description) {
}

record MappingView(String serviceName, String method, String path, JsonNode inputSchema) {
}

record ToolView(String id, String name, String description, boolean enabled, Instant createdAt, MappingView mapping) {
}
