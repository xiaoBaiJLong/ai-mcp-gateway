package com.xiaobaijlong.mcpgateway.management.authorization;

public record AgentCredentialView(
        long id,
        String name,
        String apiKeyPrefix,
        String apiKey
) {
}
