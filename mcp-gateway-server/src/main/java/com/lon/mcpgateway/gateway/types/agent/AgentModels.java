package com.lon.mcpgateway.gateway.types.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public final class AgentModels {
    private AgentModels() {
    }

    public record CreateAgentRequest(@NotBlank String name, String description) {
    }

    public record UpdateCredentialStatusRequest(boolean enabled) {
    }

    public record PublishToolSnapshotRequest(@NotNull List<@NotBlank String> toolIds) {
    }

    public record AgentCredentialView(String id, String prefix, Instant createdAt, boolean enabled) {
    }

    public record RevealedAgentCredentialView(String id, String prefix, Instant createdAt, boolean enabled, String apiKey) {
    }

    public record AgentToolView(String id, String name, String description, boolean enabled) {
    }

    public record AgentView(String id, String name, String description, Instant createdAt,
            List<AgentCredentialView> credentials, List<AgentToolView> toolSnapshot) {
    }

    public record CreatedAgentView(String id, String name, String description, Instant createdAt,
            List<AgentToolView> toolSnapshot, RevealedAgentCredentialView credential) {
    }

    public record AgentRecord(String id, String name, String description, Instant createdAt, String currentCredentialId) {
    }

    public record CredentialRecord(String id, String agentId, String keyHash, String prefix, Instant createdAt, boolean enabled) {
    }

    public record AgentToolRecord(String id, String name, String description, boolean enabled) {
    }
}
