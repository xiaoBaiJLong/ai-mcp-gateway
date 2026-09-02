package com.lon.mcpgateway.gateway.trigger.validation;

import com.lon.mcpgateway.gateway.api.validation.McpValidationCase;
import com.lon.mcpgateway.gateway.trigger.common.ApiResponse;
import com.lon.mcpgateway.gateway.types.validation.ValidationModels.ChatRequest;
import com.lon.mcpgateway.gateway.types.validation.ValidationModels.ConnectRequest;
import com.lon.mcpgateway.gateway.types.validation.ValidationModels.ConnectionView;
import com.lon.mcpgateway.gateway.types.validation.ValidationModels.ValidationEvent;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/v1/validation")
class McpValidationController {
    private final McpValidationCase validation;

    McpValidationController(McpValidationCase validation) {
        this.validation = validation;
    }

    @PostMapping("/connect")
    Mono<ApiResponse<ConnectionView>> connect(@Valid @RequestBody ConnectRequest request) {
        return Mono.fromCallable(() -> validation.connect(request.agentKey())).subscribeOn(Schedulers.boundedElastic()).map(ApiResponse::success);
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<ServerSentEvent<ValidationEvent>> chat(@Valid @RequestBody ChatRequest request) {
        return Mono.fromCallable(() -> validation.chat(request)).subscribeOn(Schedulers.boundedElastic()).flatMapMany(events -> events
                .map(event -> ServerSentEvent.builder(event).event(event.type()).build()))
                .onErrorResume(exception -> Flux.just(ServerSentEvent.builder(new ValidationEvent("error", exception.getMessage())).event("error").build()));
    }
}
