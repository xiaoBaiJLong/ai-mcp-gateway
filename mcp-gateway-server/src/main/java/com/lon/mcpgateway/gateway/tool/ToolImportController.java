package com.lon.mcpgateway.gateway.tool;

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
    private final ToolImportService toolImportService;

    ToolImportController(ToolImportService toolImportService) {
        this.toolImportService = toolImportService;
    }

    @GetMapping("/tool-sources")
    Mono<ApiResponse<List<ToolSourceView>>> sources() {
        return bounded(toolImportService::sources).map(ApiResponse::success);
    }

    @GetMapping("/tool-sources/{serviceName}/operations")
    Mono<ApiResponse<OpenApiOperationsView>> operations(@PathVariable String serviceName) {
        return bounded(() -> toolImportService.operations(serviceName)).map(ApiResponse::success);
    }

    @PostMapping("/tool-drafts")
    Mono<ApiResponse<ToolDraftView>> draft(@Valid @RequestBody DraftRequest request) {
        return bounded(() -> toolImportService.draft(request)).map(ApiResponse::success);
    }

    @PostMapping("/tools")
    Mono<ResponseEntity<ApiResponse<ToolView>>> create(@Valid @RequestBody CreateToolRequest request) {
        return bounded(() -> toolImportService.create(request))
                .map(result -> ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result)));
    }

    @GetMapping("/tools")
    Mono<ApiResponse<List<ToolView>>> tools() {
        return bounded(toolImportService::tools).map(ApiResponse::success);
    }

    private <T> Mono<T> bounded(java.util.concurrent.Callable<T> action) {
        return Mono.fromCallable(action).subscribeOn(Schedulers.boundedElastic());
    }
}
