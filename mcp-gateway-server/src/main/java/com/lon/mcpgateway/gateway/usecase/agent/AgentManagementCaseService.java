package com.lon.mcpgateway.gateway.usecase.agent;

import com.lon.mcpgateway.gateway.api.agent.AgentDomainService;
import com.lon.mcpgateway.gateway.api.agent.AgentManagementCase;
import com.lon.mcpgateway.gateway.api.toolcollection.ToolCollectionRepositoryPort;
import com.lon.mcpgateway.gateway.types.agent.AgentModels.AgentView;
import com.lon.mcpgateway.gateway.types.agent.AgentModels.CreatedAgentView;
import com.lon.mcpgateway.gateway.types.agent.AgentModels.CreateAgentRequest;
import com.lon.mcpgateway.gateway.types.agent.AgentModels.PublishToolSnapshotRequest;
import com.lon.mcpgateway.gateway.types.agent.AgentModels.RevealedAgentCredentialView;
import com.lon.mcpgateway.gateway.types.agent.AgentModels.UpdateCredentialStatusRequest;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

public class AgentManagementCaseService implements AgentManagementCase {
    private final AgentDomainService agentDomainService;
    private final ToolCollectionRepositoryPort collectionRepository;

    public AgentManagementCaseService(AgentDomainService agentDomainService, ToolCollectionRepositoryPort collectionRepository) {
        this.agentDomainService = agentDomainService;
        this.collectionRepository = collectionRepository;
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
        LinkedHashSet<String> toolIds = new LinkedHashSet<>();
        for (String collectionId : new LinkedHashSet<>(request.collectionIds())) {
            if (collectionRepository.find(collectionId) == null) {
                throw new com.lon.mcpgateway.gateway.types.common.GatewayException("TOOL_COLLECTION_NOT_FOUND", "工具集不存在");
            }
            toolIds.addAll(collectionRepository.findToolIds(collectionId));
        }
        toolIds.addAll(request.toolIds());
        return agentDomainService.publishToolSnapshot(agentId, List.copyOf(toolIds));
    }
}
