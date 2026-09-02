package com.lon.mcpgateway.gateway.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lon.mcpgateway.gateway.api.agent.AgentDomainService;
import com.lon.mcpgateway.gateway.api.agent.AgentManagementCase;
import com.lon.mcpgateway.gateway.api.agent.AgentRepositoryPort;
import com.lon.mcpgateway.gateway.api.mcp.McpRuntimeDomainService;
import com.lon.mcpgateway.gateway.api.mcp.McpToolInvocationPort;
import com.lon.mcpgateway.gateway.api.validation.McpGatewayValidationClientPort;
import com.lon.mcpgateway.gateway.api.validation.McpValidationCase;
import com.lon.mcpgateway.gateway.api.validation.OpenAiValidationChatbotPort;
import com.lon.mcpgateway.gateway.api.discovery.BusinessServiceDiscoveryPort;
import com.lon.mcpgateway.gateway.api.discovery.OpenApiDocumentPort;
import com.lon.mcpgateway.gateway.api.tool.McpToolRepositoryPort;
import com.lon.mcpgateway.gateway.api.tool.ToolImportCase;
import com.lon.mcpgateway.gateway.api.toolcollection.ToolCollectionRepositoryPort;
import com.lon.mcpgateway.gateway.usecase.agent.AgentManagementCaseService;
import com.lon.mcpgateway.gateway.usecase.tool.ToolImportCaseService;
import com.lon.mcpgateway.gateway.domain.agent.AgentDomainServiceImpl;
import com.lon.mcpgateway.gateway.domain.mcp.McpRuntimeDomainServiceImpl;
import com.lon.mcpgateway.gateway.domain.tool.OpenApiImporter;
import com.lon.mcpgateway.gateway.domain.toolcollection.ToolCollectionDomainService;
import com.lon.mcpgateway.gateway.usecase.validation.McpValidationCaseService;
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
    AgentManagementCase agentManagementCase(AgentDomainService agentDomainService, ToolCollectionRepositoryPort collectionRepository) {
        return new AgentManagementCaseService(agentDomainService, collectionRepository);
    }

    @Bean
    com.lon.mcpgateway.gateway.api.toolcollection.ToolCollectionDomainService toolCollectionDomainService(ToolCollectionRepositoryPort collectionRepository,
            McpToolRepositoryPort toolRepository) {
        return new ToolCollectionDomainService(collectionRepository, toolRepository);
    }

    @Bean
    McpRuntimeDomainService mcpRuntimeDomainService(AgentRepositoryPort agentRepository, McpToolRepositoryPort toolRepository,
            McpToolInvocationPort toolInvocation, ObjectMapper objectMapper) {
        return new McpRuntimeDomainServiceImpl(agentRepository, toolRepository, toolInvocation, objectMapper);
    }

    @Bean
    McpValidationCase mcpValidationCase(McpGatewayValidationClientPort mcpClient, OpenAiValidationChatbotPort chatbot) {
        return new McpValidationCaseService(mcpClient, chatbot);
    }

    @Bean
    ToolImportCase toolImportCase(BusinessServiceDiscoveryPort discovery, OpenApiDocumentPort documentPort,
            OpenApiImporter importer, McpToolRepositoryPort toolRepository, ObjectMapper objectMapper) {
        return new ToolImportCaseService(discovery, documentPort, importer, toolRepository, objectMapper);
    }
}
