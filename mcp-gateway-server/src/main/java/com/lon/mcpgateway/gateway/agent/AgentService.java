package com.lon.mcpgateway.gateway.agent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.lon.mcpgateway.gateway.tool.ApiException;

@Service
class AgentService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final AgentMapper agentMapper;

    AgentService(AgentMapper agentMapper) {
        this.agentMapper = agentMapper;
    }

    @Transactional
    CreatedAgentView create(CreateAgentRequest request) {
        Instant createdAt = Instant.now();
        String agentId = UUID.randomUUID().toString();
        agentMapper.insertAgent(new AgentMapper.StoredAgent(agentId, request.name(), description(request.description()), createdAt));
        IssuedCredential credential = issueCredential(agentId, createdAt);
        return new CreatedAgentView(agentId, request.name(), description(request.description()), createdAt, List.of(), credential.view());
    }

    List<AgentView> agents() {
        return agentMapper.findAllAgents().stream().map(this::view).toList();
    }

    AgentView agent(String agentId) {
        return view(requiredAgent(agentId));
    }

    @Transactional
    AgentView updateCredentialStatus(String agentId, UpdateCredentialStatusRequest request) {
        requiredAgentForUpdate(agentId);
        AgentMapper.CredentialRow credential = requiredCurrentCredential(agentId);
        if (request.enabled()) {
            agentMapper.disableEnabledCredentials(agentId);
        }
        agentMapper.updateCredentialEnabled(credential.id(), request.enabled());
        return agent(agentId);
    }

    @Transactional
    RevealedAgentCredentialView resetCredential(String agentId) {
        requiredAgentForUpdate(agentId);
        Instant createdAt = Instant.now();
        agentMapper.disableEnabledCredentials(agentId);
        return issueCredential(agentId, createdAt).view();
    }

    @Transactional
    AgentView publishToolSnapshot(String agentId, PublishToolSnapshotRequest request) {
        requiredAgentForUpdate(agentId);
        List<String> toolIds = List.copyOf(new LinkedHashSet<>(request.toolIds()));
        if (!toolIds.isEmpty() && agentMapper.findEnabledToolsByIds(toolIds).size() != toolIds.size()) {
            throw new ApiException("TOOL_NOT_PUBLISHED", HttpStatus.UNPROCESSABLE_ENTITY, "只能选择已发布的 MCP 工具");
        }
        agentMapper.deleteToolAssignments(agentId);
        toolIds.forEach(toolId -> agentMapper.insertToolAssignment(agentId, toolId));
        return agent(agentId);
    }

    private AgentView view(AgentMapper.AgentRow agent) {
        List<AgentCredentialView> credentials = agentMapper.findCredentials(agent.id()).stream()
                .map(row -> new AgentCredentialView(row.id(), row.prefix(), row.createdAt(), row.enabled())).toList();
        return new AgentView(agent.id(), agent.name(), agent.description(), agent.createdAt(), credentials, toolSnapshot(agent.id()));
    }

    private List<AgentToolView> toolSnapshot(String agentId) {
        return agentMapper.findToolSnapshot(agentId).stream()
                .map(row -> new AgentToolView(row.id(), row.name(), row.description(), row.enabled())).toList();
    }

    private AgentMapper.AgentRow requiredAgent(String agentId) {
        AgentMapper.AgentRow agent = agentMapper.findAgent(agentId);
        if (agent == null) {
            throw new ApiException("AGENT_NOT_FOUND", HttpStatus.NOT_FOUND, "智能体不存在");
        }
        return agent;
    }

    private AgentMapper.AgentRow requiredAgentForUpdate(String agentId) {
        AgentMapper.AgentRow agent = agentMapper.findAgentForUpdate(agentId);
        if (agent == null) {
            throw new ApiException("AGENT_NOT_FOUND", HttpStatus.NOT_FOUND, "智能体不存在");
        }
        return agent;
    }

    private AgentMapper.CredentialRow requiredCurrentCredential(String agentId) {
        AgentMapper.CredentialRow credential = agentMapper.findCurrentCredential(agentId);
        if (credential == null) {
            throw new ApiException("CREDENTIAL_NOT_FOUND", HttpStatus.NOT_FOUND, "智能体凭证不存在");
        }
        return credential;
    }

    private IssuedCredential issueCredential(String agentId, Instant createdAt) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String apiKey = "mcp_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String credentialId = UUID.randomUUID().toString();
        String prefix = apiKey.substring(0, Math.min(apiKey.length(), 12));
        agentMapper.insertCredential(new AgentMapper.StoredCredential(credentialId, agentId, hash(apiKey), prefix, createdAt, true));
        agentMapper.updateCurrentCredential(agentId, credentialId);
        return new IssuedCredential(new RevealedAgentCredentialView(credentialId, prefix, createdAt, true, apiKey));
    }

    private String description(String value) {
        return value == null ? "" : value;
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte item : digest) {
                hex.append(String.format("%02x", item));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 缺少 SHA-256", exception);
        }
    }

    private record IssuedCredential(RevealedAgentCredentialView view) {
    }
}
