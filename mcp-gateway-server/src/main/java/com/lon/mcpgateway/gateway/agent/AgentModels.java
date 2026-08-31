package com.lon.mcpgateway.gateway.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

record CreateAgentRequest(@NotBlank String name, String description) {
}

record UpdateCredentialStatusRequest(boolean enabled) {
}

record PublishToolSnapshotRequest(@NotNull List<@NotBlank String> toolIds) {
}

record AgentCredentialView(String id, String prefix, Instant createdAt, boolean enabled) {
}

record RevealedAgentCredentialView(String id, String prefix, Instant createdAt, boolean enabled, String apiKey) {
}

record AgentToolView(String id, String name, String description, boolean enabled) {
}

record AgentView(String id, String name, String description, Instant createdAt,
        List<AgentCredentialView> credentials, List<AgentToolView> toolSnapshot) {
}

record CreatedAgentView(String id, String name, String description, Instant createdAt,
        List<AgentToolView> toolSnapshot, RevealedAgentCredentialView credential) {
}
