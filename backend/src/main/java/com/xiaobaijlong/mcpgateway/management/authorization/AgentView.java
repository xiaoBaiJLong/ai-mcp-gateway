package com.xiaobaijlong.mcpgateway.management.authorization;

import java.time.Instant;
import java.util.List;

public record AgentView(
        long id,
        String name,
        String apiKeyPrefix,
        List<Long> roleIds,
        Instant createdAt,
        Instant updatedAt
) {
}
