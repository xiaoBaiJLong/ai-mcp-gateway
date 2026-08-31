package com.lon.mcpgateway.gateway.tool;

import com.fasterxml.jackson.databind.JsonNode;

public interface OpenApiDocumentClient {
    JsonNode fetch(String serviceName);
}
