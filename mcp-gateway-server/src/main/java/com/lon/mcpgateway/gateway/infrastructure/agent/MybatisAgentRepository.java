package com.lon.mcpgateway.gateway.infrastructure.agent;

import com.lon.mcpgateway.gateway.api.agent.AgentRepositoryPort;
import com.lon.mcpgateway.gateway.types.agent.AgentModels.AgentRecord;
import com.lon.mcpgateway.gateway.types.agent.AgentModels.AgentToolRecord;
import com.lon.mcpgateway.gateway.types.agent.AgentModels.CredentialRecord;
import com.lon.mcpgateway.gateway.types.agent.AgentModels.RuntimeAgentRecord;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisAgentRepository implements AgentRepositoryPort {
    private final AgentMapper mapper;

    public MybatisAgentRepository(AgentMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insertAgent(AgentRecord agent) {
        mapper.insertAgent(new AgentMapper.StoredAgent(agent.id(), agent.name(), agent.description(), agent.createdAt()));
    }

    @Override
    public void insertCredential(CredentialRecord credential) {
        mapper.insertCredential(new AgentMapper.StoredCredential(credential.id(), credential.agentId(), credential.keyHash(),
                credential.prefix(), credential.createdAt(), credential.enabled()));
    }

    @Override
    public AgentRecord findAgent(String agentId) {
        return agent(mapper.findAgent(agentId));
    }

    @Override
    public AgentRecord findAgentForUpdate(String agentId) {
        return agent(mapper.findAgentForUpdate(agentId));
    }

    @Override
    public List<AgentRecord> findAllAgents() {
        return mapper.findAllAgents().stream().map(this::agent).toList();
    }

    @Override
    public List<CredentialRecord> findCredentials(String agentId) {
        return mapper.findCredentials(agentId).stream().map(this::credential).toList();
    }

    @Override
    public CredentialRecord findCurrentCredential(String agentId) {
        return credential(mapper.findCurrentCredential(agentId));
    }

    @Override
    public RuntimeAgentRecord findEnabledAgentByKeyHash(String keyHash) {
        AgentMapper.RuntimeAgentRow row = mapper.findEnabledAgentByKeyHash(keyHash);
        return row == null ? null : new RuntimeAgentRecord(row.id());
    }

    @Override
    public void disableEnabledCredentials(String agentId) {
        mapper.disableEnabledCredentials(agentId);
    }

    @Override
    public void updateCredentialEnabled(String credentialId, boolean enabled) {
        mapper.updateCredentialEnabled(credentialId, enabled);
    }

    @Override
    public void updateCurrentCredential(String agentId, String credentialId) {
        mapper.updateCurrentCredential(agentId, credentialId);
    }

    @Override
    public void replaceToolAssignments(String agentId, List<String> toolIds) {
        mapper.deleteToolAssignments(agentId);
        toolIds.forEach(toolId -> mapper.insertToolAssignment(agentId, toolId));
    }

    @Override
    public List<AgentToolRecord> findToolSnapshot(String agentId) {
        return mapper.findToolSnapshot(agentId).stream()
                .map(row -> new AgentToolRecord(row.id(), row.name(), row.description(), row.enabled())).toList();
    }

    private AgentRecord agent(AgentMapper.AgentRow row) {
        return row == null ? null : new AgentRecord(row.id(), row.name(), row.description(), row.createdAt(), null);
    }

    private CredentialRecord credential(AgentMapper.CredentialRow row) {
        return row == null ? null : new CredentialRecord(row.id(), row.agentId(), null, row.prefix(), row.createdAt(), row.enabled());
    }
}
