package com.lon.mcpgateway.gateway.api.agent;

import com.lon.mcpgateway.gateway.types.agent.AgentModels.AgentRecord;
import com.lon.mcpgateway.gateway.types.agent.AgentModels.AgentToolRecord;
import com.lon.mcpgateway.gateway.types.agent.AgentModels.CredentialRecord;
import java.util.List;

public interface AgentRepositoryPort {
    void insertAgent(AgentRecord agent);

    void insertCredential(CredentialRecord credential);

    AgentRecord findAgent(String agentId);

    AgentRecord findAgentForUpdate(String agentId);

    List<AgentRecord> findAllAgents();

    List<CredentialRecord> findCredentials(String agentId);

    CredentialRecord findCurrentCredential(String agentId);

    void disableEnabledCredentials(String agentId);

    void updateCredentialEnabled(String credentialId, boolean enabled);

    void updateCurrentCredential(String agentId, String credentialId);

    void replaceToolAssignments(String agentId, List<String> toolIds);

    List<AgentToolRecord> findToolSnapshot(String agentId);
}
