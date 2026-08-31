package com.lon.mcpgateway.gateway.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lon.mcpgateway.gateway.api.agent.AgentDomainService;
import com.lon.mcpgateway.gateway.api.agent.AgentManagementCase;
import com.lon.mcpgateway.gateway.api.agent.AgentRepositoryPort;
import com.lon.mcpgateway.gateway.api.discovery.BusinessServiceDiscoveryPort;
import com.lon.mcpgateway.gateway.api.discovery.OpenApiDocumentPort;
import com.lon.mcpgateway.gateway.api.tool.McpToolRepositoryPort;
import com.lon.mcpgateway.gateway.api.tool.ToolImportCase;
import com.lon.mcpgateway.gateway.usecase.agent.AgentManagementCaseService;
import com.lon.mcpgateway.gateway.usecase.tool.ToolImportCaseService;
import com.lon.mcpgateway.gateway.domain.agent.AgentDomainServiceImpl;
import com.lon.mcpgateway.gateway.domain.tool.OpenApiImporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class GatewayApplicationConfiguration {
    @Bean
    OpenApiImporter openApiImporter() {
        return new OpenApiImporter();
    }

    @Bean
    AgentDomainService agentDomainService(AgentRepositoryPort agentRepository, McpToolRepositoryPort toolRepository) {
        return new AgentDomainServiceImpl(agentRepository, toolRepository);
    }

    @Bean
    AgentManagementCase agentManagementCase(AgentDomainService agentDomainService) {
        return new AgentManagementCaseService(agentDomainService);
    }

    @Bean
    ToolImportCase toolImportCase(BusinessServiceDiscoveryPort discovery, OpenApiDocumentPort documentPort,
            OpenApiImporter importer, McpToolRepositoryPort toolRepository, ObjectMapper objectMapper) {
        return new ToolImportCaseService(discovery, documentPort, importer, toolRepository, objectMapper);
    }
}
