package com.lon.mcpgateway.gateway.api.toolcollection;

import com.lon.mcpgateway.gateway.types.agent.AgentModels.AgentToolView;
import java.time.Instant;
import java.util.List;

public record ToolCollectionView(String id, String name, String description, Instant createdAt, List<AgentToolView> tools) {
}
