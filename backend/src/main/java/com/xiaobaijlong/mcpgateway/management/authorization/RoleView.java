package com.xiaobaijlong.mcpgateway.management.authorization;

import java.util.List;

public record RoleView(
        long id,
        String name,
        String description,
        List<Long> toolSetIds
) {
}
