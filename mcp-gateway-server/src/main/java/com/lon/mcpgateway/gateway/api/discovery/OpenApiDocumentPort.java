package com.lon.mcpgateway.gateway.api.discovery;

import com.fasterxml.jackson.databind.JsonNode;

public interface OpenApiDocumentPort {
    JsonNode fetch(String serviceName);
}
