package com.lon.mcpgateway.gateway.api.validation;

import com.lon.mcpgateway.gateway.types.validation.ValidationModels.ChatbotEvent;
import com.lon.mcpgateway.gateway.types.validation.ValidationModels.ChatbotRequest;
import reactor.core.publisher.Flux;

public interface OpenAiValidationChatbotPort {
    Flux<ChatbotEvent> respond(ChatbotRequest request);
}
