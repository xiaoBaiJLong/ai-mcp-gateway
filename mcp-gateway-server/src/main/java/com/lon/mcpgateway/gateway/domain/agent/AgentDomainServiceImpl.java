package com.lon.mcpgateway.gateway.domain.agent;

import com.lon.mcpgateway.gateway.api.agent.AgentDomainService;
import com.lon.mcpgateway.gateway.api.agent.AgentRepositoryPort;
import com.lon.mcpgateway.gateway.api.tool.McpToolRepositoryPort;
import com.lon.mcpgateway.gateway.types.agent.AgentModels.AgentCredentialView;
import com.lon.mcpgateway.gateway.types.agent.AgentModels.AgentRecord;
import com.lon.mcpgateway.gateway.types.agent.AgentModels.AgentToolRecord;
import com.lon.mcpgateway.gateway.types.agent.AgentModels.AgentToolView;
import com.lon.mcpgateway.gateway.types.agent.AgentModels.AgentView;
import com.lon.mcpgateway.gateway.types.agent.AgentModels.CreatedAgentView;
import com.lon.mcpgateway.gateway.types.agent.AgentModels.CreateAgentRequest;
import com.lon.mcpgateway.gateway.types.agent.AgentModels.CredentialRecord;
import com.lon.mcpgateway.gateway.types.agent.AgentModels.RevealedAgentCredentialView;
import com.lon.mcpgateway.gateway.types.agent.AgentModels.UpdateCredentialStatusRequest;
import com.lon.mcpgateway.gateway.types.common.GatewayException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

public final class AgentDomainServiceImpl implements AgentDomainService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final AgentRepositoryPort agentRepository;
    private final McpToolRepositoryPort toolRepository;

    public AgentDomainServiceImpl(AgentRepositoryPort agentRepository, McpToolRepositoryPort toolRepository) {
        this.agentRepository = agentRepository;
        this.toolRepository = toolRepository;
    }

    public CreatedAgentView create(CreateAgentRequest request) {
        Instant createdAt = Instant.now();
        String agentId = UUID.randomUUID().toString();
        agentRepository.insertAgent(new AgentRecord(agentId, request.name(), description(request.description()), createdAt, null));
        IssuedCredential credential = issueCredential(agentId, createdAt);
        return new CreatedAgentView(agentId, request.name(), description(request.description()), createdAt, List.of(), credential.view());
    }

    public List<AgentView> agents() {
        return agentRepository.findAllAgents().stream().map(this::view).toList();
    }

    public AgentView agent(String agentId) {
        return view(requiredAgent(agentId));
    }

    public AgentView updateCredentialStatus(String agentId, UpdateCredentialStatusRequest request) {
        requiredAgentForUpdate(agentId);
        CredentialRecord credential = requiredCurrentCredential(agentId);
        if (request.enabled()) {
            agentRepository.disableEnabledCredentials(agentId);
        }
        agentRepository.updateCredentialEnabled(credential.id(), request.enabled());
        return agent(agentId);
    }

    public RevealedAgentCredentialView resetCredential(String agentId) {
        requiredAgentForUpdate(agentId);
        Instant createdAt = Instant.now();
        agentRepository.disableEnabledCredentials(agentId);
        return issueCredential(agentId, createdAt).view();
    }

    public AgentView publishToolSnapshot(String agentId, List<String> requestedToolIds) {
        requiredAgentForUpdate(agentId);
        List<String> toolIds = List.copyOf(new LinkedHashSet<>(requestedToolIds));
        if (!toolIds.isEmpty() && toolRepository.findEnabledToolIds(toolIds).size() != toolIds.size()) {
            throw new GatewayException("TOOL_NOT_PUBLISHED", "只能选择已发布的 MCP 工具");
        }
        agentRepository.replaceToolAssignments(agentId, toolIds);
        return agent(agentId);
    }

    private AgentView view(AgentRecord agent) {
        List<AgentCredentialView> credentials = agentRepository.findCredentials(agent.id()).stream()
                .map(row -> new AgentCredentialView(row.id(), row.prefix(), row.createdAt(), row.enabled())).toList();
        return new AgentView(agent.id(), agent.name(), agent.description(), agent.createdAt(), credentials, toolSnapshot(agent.id()));
    }

    private List<AgentToolView> toolSnapshot(String agentId) {
        return agentRepository.findToolSnapshot(agentId).stream()
                .map(row -> new AgentToolView(row.id(), row.name(), row.description(), row.enabled())).toList();
    }

    private AgentRecord requiredAgent(String agentId) {
        AgentRecord agent = agentRepository.findAgent(agentId);
        if (agent == null) {
            throw new GatewayException("AGENT_NOT_FOUND", "智能体不存在");
        }
        return agent;
    }

    private AgentRecord requiredAgentForUpdate(String agentId) {
        AgentRecord agent = agentRepository.findAgentForUpdate(agentId);
        if (agent == null) {
            throw new GatewayException("AGENT_NOT_FOUND", "智能体不存在");
        }
        return agent;
    }

    private CredentialRecord requiredCurrentCredential(String agentId) {
        CredentialRecord credential = agentRepository.findCurrentCredential(agentId);
        if (credential == null) {
            throw new GatewayException("CREDENTIAL_NOT_FOUND", "智能体凭证不存在");
        }
        return credential;
    }

    private IssuedCredential issueCredential(String agentId, Instant createdAt) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String apiKey = "mcp_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String credentialId = UUID.randomUUID().toString();
        String prefix = apiKey.substring(0, Math.min(apiKey.length(), 12));
        agentRepository.insertCredential(new CredentialRecord(credentialId, agentId, hash(apiKey), prefix, createdAt, true));
        agentRepository.updateCurrentCredential(agentId, credentialId);
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
