package com.lon.mcpgateway.gateway.api.agent;

import com.lon.mcpgateway.gateway.types.agent.AgentModels.AgentView;
import com.lon.mcpgateway.gateway.types.agent.AgentModels.CreatedAgentView;
import com.lon.mcpgateway.gateway.types.agent.AgentModels.CreateAgentRequest;
import com.lon.mcpgateway.gateway.types.agent.AgentModels.RevealedAgentCredentialView;
import com.lon.mcpgateway.gateway.types.agent.AgentModels.UpdateCredentialStatusRequest;
import java.util.List;

public interface AgentDomainService {
    CreatedAgentView create(CreateAgentRequest request);

    List<AgentView> agents();

    AgentView agent(String agentId);

    AgentView updateCredentialStatus(String agentId, UpdateCredentialStatusRequest request);

    RevealedAgentCredentialView resetCredential(String agentId);

    AgentView publishToolSnapshot(String agentId, List<String> toolIds);
}
