package com.lon.mcpgateway.gateway.api.agent;

import com.lon.mcpgateway.gateway.types.agent.AgentModels.AgentView;
import com.lon.mcpgateway.gateway.types.agent.AgentModels.CreatedAgentView;
import com.lon.mcpgateway.gateway.types.agent.AgentModels.CreateAgentRequest;
import com.lon.mcpgateway.gateway.types.agent.AgentModels.PublishToolSnapshotRequest;
import com.lon.mcpgateway.gateway.types.agent.AgentModels.RevealedAgentCredentialView;
import com.lon.mcpgateway.gateway.types.agent.AgentModels.UpdateCredentialStatusRequest;

public interface AgentManagementCase {
    CreatedAgentView create(CreateAgentRequest request);

    AgentView updateCredentialStatus(String agentId, UpdateCredentialStatusRequest request);

    RevealedAgentCredentialView resetCredential(String agentId);

    AgentView publishToolSnapshot(String agentId, PublishToolSnapshotRequest request);
}
