package com.lon.mcpgateway.mockproduct.api;

import com.lon.mcpgateway.mockproduct.config.MockProductProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping(path = "/api/products", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "商品模拟服务", description = "用于验证商品查询、搜索与更新")
public class ProductController {
    private final MockProductProperties properties;
    private final Map<String, ProductResponse> products = new ConcurrentHashMap<>();

    public ProductController(MockProductProperties properties) {
        this.properties = properties;
        products.put("p-100", new ProductResponse("p-100", "智能键盘", "electronics", new BigDecimal("199.00"), true));
        products.put("p-200", new ProductResponse("p-200", "办公鼠标", "electronics", new BigDecimal("59.00"), true));
    }

    @GetMapping("/{productId}")
    @Operation(summary = "按商品 ID 查询商品")
    public Mono<ResponseEntity<?>> getProduct(@PathVariable String productId) {
        if ("slow".equals(productId)) {
            ProductResponse response = new ProductResponse("slow", "慢响应商品", "demo", BigDecimal.ONE, true);
            return Mono.delay(properties.getSlowResponseDelay()).map(ignored -> ResponseEntity.ok(response));
        }
        if ("server-error".equals(productId)) return error(500, "PRODUCT_SERVICE_FAILED", "商品服务模拟失败");
        ProductResponse product = products.get(productId);
        return product == null ? error(404, "PRODUCT_NOT_FOUND", "未找到指定商品") : Mono.just(ResponseEntity.ok(product));
    }

    @PostMapping(path = "/search", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "搜索商品")
    public Mono<ResponseEntity<?>> searchProducts(@Valid @RequestBody ProductSearchRequest request) {
        if ("server-error".equals(request.keyword())) return error(500, "PRODUCT_SEARCH_FAILED", "商品搜索模拟失败");
        if ("slow".equals(request.keyword())) {
            return Mono.delay(properties.getSlowResponseDelay()).map(ignored -> ResponseEntity.ok(search(request)));
        }
        return Mono.just(ResponseEntity.ok(search(request)));
    }

    @PatchMapping(path = "/{productId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "更新商品")
    public Mono<ResponseEntity<?>> updateProduct(@PathVariable String productId,
            @Valid @RequestBody UpdateProductRequest request) {
        if ("server-error".equals(productId)) return error(500, "PRODUCT_UPDATE_FAILED", "商品更新模拟失败");
        synchronized (products) {
            ProductResponse current = products.get(productId);
            if (current == null) return error(404, "PRODUCT_NOT_FOUND", "未找到指定商品");
            if (request.name() == null && request.price() == null && request.active() == null) {
                return error(400, "EMPTY_PRODUCT_UPDATE", "至少提供一个待更新字段");
            }
            ProductResponse updated = new ProductResponse(current.id(), request.name() == null ? current.name() : request.name(),
                    current.category(), request.price() == null ? current.price() : request.price(),
                    request.active() == null ? current.active() : request.active());
            products.put(productId, updated);
            return Mono.just(ResponseEntity.ok(updated));
        }
    }

    private ProductSearchResponse search(ProductSearchRequest request) {
        String keyword = request.keyword().toLowerCase();
        List<ProductResponse> items = products.values().stream()
                .filter(product -> product.name().toLowerCase().contains(keyword) || product.id().contains(keyword))
                .filter(product -> request.category() == null || request.category().equals(product.category()))
                .sorted((left, right) -> left.id().compareTo(right.id())).toList();
        return new ProductSearchResponse(request.keyword(), request.category(), request.page(), items);
    }

    private Mono<ResponseEntity<?>> error(int status, String code, String message) {
        return Mono.just(ResponseEntity.status(status).body(new ErrorResponse(code, message)));
    }

    public record ProductResponse(String id, String name, String category, BigDecimal price, boolean active) {}
    public record ProductSearchRequest(@NotBlank String keyword, String category, @Min(1) Integer page) {
        public ProductSearchRequest { if (page == null) page = 1; }
    }
    public record ProductSearchResponse(String keyword, String category, Integer page, List<ProductResponse> items) {}
    public record UpdateProductRequest(@Pattern(regexp = ".*\\S.*") String name,
            @DecimalMin("0.01") BigDecimal price, Boolean active) {}
    public record ErrorResponse(String code, String message) {}
}
