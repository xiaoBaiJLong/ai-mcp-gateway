package com.lon.mcpgateway.mockorder.api;

import com.lon.mcpgateway.mockorder.config.MockOrderProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
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
@RequestMapping(path = "/api/orders", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "订单模拟服务", description = "用于验证订单查询、创建与状态流转")
public class OrderController {
    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
            OrderStatus.CREATED, Set.of(OrderStatus.PAID, OrderStatus.CANCELLED),
            OrderStatus.PAID, Set.of(OrderStatus.FULFILLING, OrderStatus.CANCELLED),
            OrderStatus.FULFILLING, Set.of(OrderStatus.COMPLETED),
            OrderStatus.COMPLETED, Set.of(),
            OrderStatus.CANCELLED, Set.of());

    private final MockOrderProperties properties;
    private final Map<String, OrderResponse> orders = new ConcurrentHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger(101);

    public OrderController(MockOrderProperties properties) {
        this.properties = properties;
        List<OrderItem> items = List.of(new OrderItem("p-100", 2, new BigDecimal("199.00")));
        orders.put("o-100", new OrderResponse("o-100", "u-100", items, new BigDecimal("398.00"), OrderStatus.FULFILLING));
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "按订单 ID 查询订单")
    public Mono<ResponseEntity<?>> getOrder(@PathVariable String orderId) {
        if ("slow".equals(orderId)) {
            OrderResponse response = new OrderResponse("slow", "u-100", List.of(), BigDecimal.ZERO, OrderStatus.CREATED);
            return Mono.delay(properties.getSlowResponseDelay()).map(ignored -> ResponseEntity.ok(response));
        }
        ResponseEntity<?> scenario = lookupScenario(orderId, "ORDER_NOT_FOUND", "未找到指定订单", "ORDER_SERVICE_FAILED", "订单服务模拟失败");
        if (scenario != null) {
            return Mono.just(scenario);
        }
        OrderResponse order = orders.get(orderId);
        return Mono.just(order == null
                ? ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("ORDER_NOT_FOUND", "未找到指定订单"))
                : ResponseEntity.ok(order));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "创建订单")
    public Mono<ResponseEntity<?>> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        if ("server-error".equals(request.userId())) {
            return Mono.just(ResponseEntity.status(500).body(new ErrorResponse("ORDER_CREATE_FAILED", "订单创建模拟失败")));
        }
        if ("not-found".equals(request.userId())) {
            return Mono.just(ResponseEntity.status(404).body(new ErrorResponse("USER_NOT_FOUND", "未找到指定用户")));
        }
        if ("slow".equals(request.userId())) {
            return Mono.delay(properties.getSlowResponseDelay()).map(ignored -> create(request));
        }
        return Mono.just(create(request));
    }

    @PatchMapping(path = "/{orderId}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "更新订单状态")
    public Mono<ResponseEntity<?>> updateStatus(@PathVariable String orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        ResponseEntity<?> scenario = lookupScenario(orderId, "ORDER_NOT_FOUND", "未找到指定订单", "ORDER_UPDATE_FAILED", "订单更新模拟失败");
        if (scenario != null) {
            return Mono.just(scenario);
        }
        synchronized (orders) {
            OrderResponse current = orders.get(orderId);
            if (current == null) {
                return Mono.just(ResponseEntity.status(404).body(new ErrorResponse("ORDER_NOT_FOUND", "未找到指定订单")));
            }
            if (!TRANSITIONS.get(current.status()).contains(request.status())) {
                return Mono.just(ResponseEntity.status(409).body(new ErrorResponse("INVALID_ORDER_TRANSITION", "订单状态不允许这样流转")));
            }
            OrderResponse updated = new OrderResponse(current.id(), current.userId(), current.items(), current.totalAmount(), request.status());
            orders.put(orderId, updated);
            return Mono.just(ResponseEntity.ok(updated));
        }
    }

    private synchronized ResponseEntity<?> create(CreateOrderRequest request) {
        String id = "o-" + sequence.getAndIncrement();
        List<OrderItem> items = request.items().stream()
                .map(item -> new OrderItem(item.productId(), item.quantity(), item.unitPrice()))
                .toList();
        BigDecimal total = items.stream()
                .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        OrderResponse created = new OrderResponse(id, request.userId(), items, total, OrderStatus.CREATED);
        orders.put(id, created);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    private ResponseEntity<?> lookupScenario(String id, String notFoundCode, String notFoundMessage,
            String errorCode, String errorMessage) {
        if ("not-found".equals(id)) return ResponseEntity.status(404).body(new ErrorResponse(notFoundCode, notFoundMessage));
        if ("server-error".equals(id)) return ResponseEntity.status(500).body(new ErrorResponse(errorCode, errorMessage));
        return null;
    }

    public enum OrderStatus { CREATED, PAID, FULFILLING, COMPLETED, CANCELLED }
    public record OrderItem(String productId, int quantity, BigDecimal unitPrice) {}
    public record OrderResponse(String id, String userId, List<OrderItem> items, BigDecimal totalAmount, OrderStatus status) {}
    public record CreateOrderItem(@NotBlank String productId, @Min(1) int quantity,
            @NotNull @DecimalMin("0.01") BigDecimal unitPrice) {}
    public record CreateOrderRequest(@NotBlank String userId, @NotEmpty List<@Valid CreateOrderItem> items) {}
    public record UpdateOrderStatusRequest(@NotNull OrderStatus status) {}
    public record ErrorResponse(String code, String message) {}
}
