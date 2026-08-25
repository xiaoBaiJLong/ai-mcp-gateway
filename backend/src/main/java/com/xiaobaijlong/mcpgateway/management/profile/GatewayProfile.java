package com.xiaobaijlong.mcpgateway.management.profile;

import java.time.Instant;

public record GatewayProfile(
        String name,
        String updatedBy,
        Instant updatedAt
) {
}
