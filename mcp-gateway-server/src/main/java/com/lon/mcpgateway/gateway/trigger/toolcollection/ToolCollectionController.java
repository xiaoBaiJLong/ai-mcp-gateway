package com.lon.mcpgateway.gateway.trigger.toolcollection;

import com.lon.mcpgateway.gateway.api.toolcollection.ToolCollectionDomainService;
import com.lon.mcpgateway.gateway.api.toolcollection.ToolCollectionView;
import com.lon.mcpgateway.gateway.trigger.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.concurrent.Callable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/v1/tool-collections")
public final class ToolCollectionController {
    private final ToolCollectionDomainService domainService;

    public ToolCollectionController(ToolCollectionDomainService domainService) {
        this.domainService = domainService;
    }

    @GetMapping
    Mono<ApiResponse<List<ToolCollectionView>>> collections() {
        return bounded(domainService::collections).map(ApiResponse::success);
    }

    @PostMapping
    Mono<ResponseEntity<ApiResponse<ToolCollectionView>>> create(@Valid @RequestBody ToolCollectionRequest request) {
        return bounded(() -> domainService.create(request.command()))
                .map(value -> ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(value)));
    }

    @PutMapping("/{collectionId}")
    Mono<ApiResponse<ToolCollectionView>> update(@PathVariable String collectionId, @Valid @RequestBody ToolCollectionRequest request) {
        return bounded(() -> domainService.update(collectionId, request.command())).map(ApiResponse::success);
    }

    @DeleteMapping("/{collectionId}")
    Mono<ResponseEntity<Void>> delete(@PathVariable String collectionId) {
        return bounded(() -> {
            domainService.delete(collectionId);
            return ResponseEntity.noContent().build();
        });
    }

    private <T> Mono<T> bounded(Callable<T> action) {
        return Mono.fromCallable(action).subscribeOn(Schedulers.boundedElastic());
    }
}
