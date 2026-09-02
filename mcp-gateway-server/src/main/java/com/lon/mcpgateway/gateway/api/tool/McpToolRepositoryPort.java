package com.lon.mcpgateway.gateway.api.tool;

import com.lon.mcpgateway.gateway.types.tool.ToolModels.HttpMappingRecord;
import com.lon.mcpgateway.gateway.types.tool.ToolModels.McpToolRecord;
import com.lon.mcpgateway.gateway.types.tool.ToolModels.StoredToolView;
import com.lon.mcpgateway.gateway.types.tool.ToolModels.RuntimeToolRecord;
import java.util.List;

public interface McpToolRepositoryPort {
    int countByNameHash(String nameHash);

    int countBySourceHash(String sourceHash);

    void save(McpToolRecord tool, HttpMappingRecord mapping);

    List<StoredToolView> findAll();

    List<String> findEnabledToolIds(List<String> toolIds);

    List<String> findToolIds(List<String> toolIds);

    List<RuntimeToolRecord> findEnabledToolsForAgent(String agentId);

    RuntimeToolRecord findEnabledToolForAgent(String agentId, String toolName);
}
