package com.xiaobaijlong.mcpgateway.management.tool;

import java.time.Instant;
import java.util.List;

public record ToolDraftView(
        long id,
        String toolName,
        String displayName,
        RiskLevel riskLevel,
        long upstreamId,
        String serviceId,
        String httpMethod,
        String path,
        String requestConfig,
        String responseConfig,
        ValidationStatus validationStatus,
        List<String> validationErrors,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {
}
