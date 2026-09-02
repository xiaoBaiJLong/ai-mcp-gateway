package com.lon.mcpgateway.gateway.api.toolcollection;

import java.time.Instant;

public record ToolCollectionRecord(String id, String name, String description, Instant createdAt) {
}
