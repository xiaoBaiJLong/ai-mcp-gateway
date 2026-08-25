package com.xiaobaijlong.mcpgateway.management.tool;

import java.time.Instant;

public record ToolVersionView(
        long id,
        String toolName,
        int versionNumber,
        String displayName,
        RiskLevel riskLevel,
        long upstreamId,
        String serviceId,
        String httpMethod,
        String path,
        String requestConfig,
        String responseConfig,
        boolean current,
        String publishedBy,
        Instant publishedAt
) {
}
