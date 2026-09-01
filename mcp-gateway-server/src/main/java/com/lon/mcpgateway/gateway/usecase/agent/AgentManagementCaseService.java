package com.lon.mcpgateway.gateway.usecase.agent;

import com.lon.mcpgateway.gateway.api.agent.AgentDomainService;
import com.lon.mcpgateway.gateway.api.agent.AgentManagementCase;
import com.lon.mcpgateway.gateway.types.agent.AgentModels.AgentView;
import com.lon.mcpgateway.gateway.types.agent.AgentModels.CreatedAgentView;
import com.lon.mcpgateway.gateway.types.agent.AgentModels.CreateAgentRequest;
import com.lon.mcpgateway.gateway.types.agent.AgentModels.PublishToolSnapshotRequest;
import com.lon.mcpgateway.gateway.types.agent.AgentModels.RevealedAgentCredentialView;
import com.lon.mcpgateway.gateway.types.agent.AgentModels.UpdateCredentialStatusRequest;
import org.springframework.transaction.annotation.Transactional;

public class AgentManagementCaseService implements AgentManagementCase {
    private final AgentDomainService agentDomainService;

    public AgentManagementCaseService(AgentDomainService agentDomainService) {
        this.agentDomainService = agentDomainService;
    }

    @Override
    @Transactional
    public CreatedAgentView create(CreateAgentRequest request) {
        return agentDomainService.create(request);
    }

    @Override
    @Transactional
    public AgentView updateCredentialStatus(String agentId, UpdateCredentialStatusRequest request) {
        return agentDomainService.updateCredentialStatus(agentId, request);
    }

    @Override
    @Transactional
    public RevealedAgentCredentialView resetCredential(String agentId) {
        return agentDomainService.resetCredential(agentId);
    }

    @Override
    @Transactional
    public AgentView publishToolSnapshot(String agentId, PublishToolSnapshotRequest request) {
        return agentDomainService.publishToolSnapshot(agentId, request.toolIds());
    }
}
