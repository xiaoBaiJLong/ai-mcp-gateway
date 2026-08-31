package com.lon.mcpgateway.gateway.infrastructure.tool;

import com.lon.mcpgateway.gateway.api.tool.McpToolRepositoryPort;
import com.lon.mcpgateway.gateway.types.tool.ToolModels.HttpMappingRecord;
import com.lon.mcpgateway.gateway.types.tool.ToolModels.McpToolRecord;
import com.lon.mcpgateway.gateway.types.tool.ToolModels.StoredToolView;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisMcpToolRepository implements McpToolRepositoryPort {
    private final ToolMapper mapper;

    public MybatisMcpToolRepository(ToolMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public int countByNameHash(String nameHash) {
        return mapper.countByNameHash(nameHash);
    }

    @Override
    public int countBySourceHash(String sourceHash) {
        return mapper.countBySourceHash(sourceHash);
    }

    @Override
    public void save(McpToolRecord tool, HttpMappingRecord mapping) {
        mapper.insertTool(new ToolMapper.StoredTool(tool.id(), tool.name(), tool.nameHash(), tool.description(), tool.enabled(), tool.createdAt()));
        mapper.insertMapping(new ToolMapper.StoredMapping(mapping.toolId(), mapping.serviceName(), mapping.method(), mapping.path(),
                mapping.sourceHash(), mapping.inputSchema(), mapping.operationSnapshot()));
    }

    @Override
    public List<StoredToolView> findAll() {
        return mapper.findAll().stream().map(row -> new StoredToolView(row.id(), row.name(), row.description(), row.enabled(),
                row.createdAt(), row.serviceName(), row.method(), row.path(), row.inputSchema())).toList();
    }

    @Override
    public List<String> findEnabledToolIds(List<String> toolIds) {
        return mapper.findEnabledToolsByIds(toolIds).stream().map(ToolMapper.EnabledToolRow::id).toList();
    }
}
