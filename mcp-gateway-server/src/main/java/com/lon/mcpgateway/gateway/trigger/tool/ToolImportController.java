package com.lon.mcpgateway.gateway.trigger.tool;

import com.lon.mcpgateway.gateway.api.tool.ToolImportCase;
import com.lon.mcpgateway.gateway.trigger.common.ApiResponse;
import com.lon.mcpgateway.gateway.types.tool.ToolModels.CreateToolRequest;
import com.lon.mcpgateway.gateway.types.tool.ToolModels.DraftRequest;
import com.lon.mcpgateway.gateway.types.tool.ToolModels.OpenApiOperationsView;
import com.lon.mcpgateway.gateway.types.tool.ToolModels.ToolDraftView;
import com.lon.mcpgateway.gateway.types.tool.ToolModels.ToolSourceView;
import com.lon.mcpgateway.gateway.types.tool.ToolModels.ToolView;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/v1")
class ToolImportController {
    private final ToolImportCase toolImportCase;

    ToolImportController(ToolImportCase toolImportCase) {
        this.toolImportCase = toolImportCase;
    }

    @GetMapping("/tool-sources")
    Mono<ApiResponse<List<ToolSourceView>>> sources() {
        return bounded(toolImportCase::sources).map(ApiResponse::success);
    }

    @GetMapping("/tool-sources/{serviceName}/operations")
    Mono<ApiResponse<OpenApiOperationsView>> operations(@PathVariable String serviceName) {
        return bounded(() -> toolImportCase.operations(serviceName)).map(ApiResponse::success);
    }

    @PostMapping("/tool-drafts")
    Mono<ApiResponse<ToolDraftView>> draft(@Valid @RequestBody DraftRequest request) {
        return bounded(() -> toolImportCase.draft(request)).map(ApiResponse::success);
    }

    @PostMapping("/tools")
    Mono<ResponseEntity<ApiResponse<ToolView>>> create(@Valid @RequestBody CreateToolRequest request) {
        return bounded(() -> toolImportCase.create(request))
                .map(result -> ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result)));
    }

    @GetMapping("/tools")
    Mono<ApiResponse<List<ToolView>>> tools() {
        return bounded(toolImportCase::tools).map(ApiResponse::success);
    }

    private <T> Mono<T> bounded(java.util.concurrent.Callable<T> action) {
        return Mono.fromCallable(action).subscribeOn(Schedulers.boundedElastic());
    }
}
