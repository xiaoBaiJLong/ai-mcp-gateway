package com.lon.mcpgateway.gateway.trigger.toolcollection;

import com.lon.mcpgateway.gateway.api.toolcollection.ToolCollectionCommand;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

record ToolCollectionRequest(@NotBlank String name, String description, List<@NotBlank String> toolIds) {
    ToolCollectionCommand command() {
        return new ToolCollectionCommand(name, description, toolIds);
    }
}
