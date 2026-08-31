package com.lon.mcpgateway.gateway.agent;

import com.lon.mcpgateway.gateway.tool.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.concurrent.Callable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/v1/agents")
class AgentController {
    private final AgentService agentService;

    AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping
    Mono<ResponseEntity<ApiResponse<CreatedAgentView>>> create(@Valid @RequestBody CreateAgentRequest request) {
        return bounded(() -> agentService.create(request))
                .map(result -> ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result)));
    }

    @GetMapping
    Mono<ApiResponse<List<AgentView>>> agents() {
        return bounded(agentService::agents).map(ApiResponse::success);
    }

    @GetMapping("/{agentId}")
    Mono<ApiResponse<AgentView>> agent(@PathVariable String agentId) {
        return bounded(() -> agentService.agent(agentId)).map(ApiResponse::success);
    }

    @PatchMapping("/{agentId}/credential")
    Mono<ApiResponse<AgentView>> updateCredentialStatus(@PathVariable String agentId,
            @Valid @RequestBody UpdateCredentialStatusRequest request) {
        return bounded(() -> agentService.updateCredentialStatus(agentId, request)).map(ApiResponse::success);
    }

    @PostMapping("/{agentId}/credential/reset")
    Mono<ApiResponse<RevealedAgentCredentialView>> resetCredential(@PathVariable String agentId) {
        return bounded(() -> agentService.resetCredential(agentId)).map(ApiResponse::success);
    }

    @PutMapping("/{agentId}/tool-snapshot")
    Mono<ApiResponse<AgentView>> publishToolSnapshot(@PathVariable String agentId,
            @Valid @RequestBody PublishToolSnapshotRequest request) {
        return bounded(() -> agentService.publishToolSnapshot(agentId, request)).map(ApiResponse::success);
    }

    private <T> Mono<T> bounded(Callable<T> action) {
        return Mono.fromCallable(action).subscribeOn(Schedulers.boundedElastic());
    }
}
