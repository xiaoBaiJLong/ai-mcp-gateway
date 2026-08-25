package com.xiaobaijlong.mcpgateway.management.tool;

import java.time.Instant;

public record StaticUpstreamView(
        long id,
        String serviceId,
        String displayName,
        String baseUrl,
        ConnectivityStatus connectivityStatus,
        String connectivityError,
        Instant lastCheckedAt,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {
}
