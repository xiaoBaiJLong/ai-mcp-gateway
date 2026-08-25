package com.xiaobaijlong.mcpgateway.management.auth;

public record SessionResponse(
        String username,
        ControlPlaneRole role,
        String csrfToken
) {
}
