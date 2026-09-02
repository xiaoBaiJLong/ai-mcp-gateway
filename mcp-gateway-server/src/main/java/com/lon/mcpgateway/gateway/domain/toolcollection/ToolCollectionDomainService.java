package com.lon.mcpgateway.gateway.domain.toolcollection;

import com.lon.mcpgateway.gateway.api.tool.McpToolRepositoryPort;
import com.lon.mcpgateway.gateway.api.toolcollection.ToolCollectionCommand;
import com.lon.mcpgateway.gateway.api.toolcollection.ToolCollectionRecord;
import com.lon.mcpgateway.gateway.api.toolcollection.ToolCollectionRepositoryPort;
import com.lon.mcpgateway.gateway.api.toolcollection.ToolCollectionView;
import com.lon.mcpgateway.gateway.types.agent.AgentModels.AgentToolView;
import com.lon.mcpgateway.gateway.types.common.GatewayException;
import com.lon.mcpgateway.gateway.types.tool.ToolModels.StoredToolView;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

public class ToolCollectionDomainService implements com.lon.mcpgateway.gateway.api.toolcollection.ToolCollectionDomainService {
    private final ToolCollectionRepositoryPort collectionRepository;
    private final McpToolRepositoryPort toolRepository;

    public ToolCollectionDomainService(ToolCollectionRepositoryPort collectionRepository, McpToolRepositoryPort toolRepository) {
        this.collectionRepository = collectionRepository;
        this.toolRepository = toolRepository;
    }

    public List<ToolCollectionView> collections() {
        return collectionRepository.findAll().stream().map(this::view).toList();
    }

    @Transactional
    public ToolCollectionView create(ToolCollectionCommand command) {
        ToolCollectionRecord collection = new ToolCollectionRecord(UUID.randomUUID().toString(), command.name(), description(command.description()), Instant.now());
        collectionRepository.insert(collection, toolIds(command.toolIds()));
        return view(collection);
    }

    @Transactional
    public ToolCollectionView update(String collectionId, ToolCollectionCommand command) {
        ToolCollectionRecord existing = required(collectionId);
        ToolCollectionRecord collection = new ToolCollectionRecord(existing.id(), command.name(), description(command.description()), existing.createdAt());
        collectionRepository.update(collection, toolIds(command.toolIds()));
        return view(collection);
    }

    @Transactional
    public void delete(String collectionId) {
        required(collectionId);
        collectionRepository.delete(collectionId);
    }

    private ToolCollectionView view(ToolCollectionRecord collection) {
        Map<String, StoredToolView> tools = toolRepository.findAll().stream()
                .collect(Collectors.toMap(StoredToolView::id, Function.identity()));
        List<AgentToolView> members = collectionRepository.findToolIds(collection.id()).stream()
                .map(tools::get).filter(java.util.Objects::nonNull)
                .map(tool -> new AgentToolView(tool.id(), tool.name(), tool.description(), tool.enabled())).toList();
        return new ToolCollectionView(collection.id(), collection.name(), collection.description(), collection.createdAt(), members);
    }

    private ToolCollectionRecord required(String collectionId) {
        ToolCollectionRecord collection = collectionRepository.find(collectionId);
        if (collection == null) {
            throw new GatewayException("TOOL_COLLECTION_NOT_FOUND", "工具集不存在");
        }
        return collection;
    }

    private List<String> toolIds(List<String> requestedToolIds) {
        List<String> toolIds = List.copyOf(new LinkedHashSet<>(requestedToolIds));
        if (!toolIds.isEmpty() && toolRepository.findToolIds(toolIds).size() != toolIds.size()) {
            throw new GatewayException("TOOL_NOT_FOUND", "MCP 工具不存在");
        }
        return toolIds;
    }

    private String description(String value) {
        return value == null ? "" : value;
    }
}
