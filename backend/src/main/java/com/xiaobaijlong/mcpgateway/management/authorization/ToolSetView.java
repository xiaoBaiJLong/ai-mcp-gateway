package com.xiaobaijlong.mcpgateway.management.authorization;

import java.util.List;

public record ToolSetView(
        long id,
        String name,
        String description,
        List<String> toolNames
) {
}
