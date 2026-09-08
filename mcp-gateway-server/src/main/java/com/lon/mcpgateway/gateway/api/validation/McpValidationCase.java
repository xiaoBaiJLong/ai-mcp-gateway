package com.lon.mcpgateway.gateway.api.validation;

import com.lon.mcpgateway.gateway.types.validation.ValidationModels.ChatRequest;
import com.lon.mcpgateway.gateway.types.validation.ValidationModels.ConnectionView;
import com.lon.mcpgateway.gateway.types.validation.ValidationModels.ValidationEvent;
import reactor.core.publisher.Flux;

public interface McpValidationCase {
    ConnectionView connect(String agentKey);

    Flux<ValidationEvent> chat(ChatRequest request);
}
