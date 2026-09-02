package com.lon.mcpgateway.gateway.api.toolcollection;

import java.util.List;

public record ToolCollectionCommand(String name, String description, List<String> toolIds) {
    public ToolCollectionCommand {
        toolIds = toolIds == null ? List.of() : List.copyOf(toolIds);
    }
}
