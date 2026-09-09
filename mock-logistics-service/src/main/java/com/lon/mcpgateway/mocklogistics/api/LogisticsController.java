package com.lon.mcpgateway.mocklogistics.api;

import com.lon.mcpgateway.mocklogistics.config.MockLogisticsProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
@RequestMapping(path = "/api/shipments", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "物流模拟服务", description = "用于验证运单查询、创建与状态流转")
public class LogisticsController {
    private static final Map<ShipmentStatus, Set<ShipmentStatus>> TRANSITIONS = Map.of(
            ShipmentStatus.CREATED, Set.of(ShipmentStatus.PICKED_UP),
            ShipmentStatus.PICKED_UP, Set.of(ShipmentStatus.IN_TRANSIT),
            ShipmentStatus.IN_TRANSIT, Set.of(ShipmentStatus.DELIVERED),
            ShipmentStatus.DELIVERED, Set.of());

    private final MockLogisticsProperties properties;
    private final Map<String, ShipmentResponse> shipments = new ConcurrentHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger(101);

    public LogisticsController(MockLogisticsProperties properties) {
        this.properties = properties;
        shipments.put("s-100", new ShipmentResponse("s-100", "o-100", "SF", "演示地址",
                ShipmentStatus.IN_TRANSIT, "上海转运中心"));
    }

    @GetMapping("/{shipmentId}")
    @Operation(summary = "按运单 ID 查询物流")
    public Mono<ResponseEntity<?>> getShipment(@PathVariable String shipmentId) {
        if ("slow".equals(shipmentId)) {
            ShipmentResponse response = new ShipmentResponse("slow", "o-100", "SF", "演示地址",
                    ShipmentStatus.CREATED, "待揽收");
            return Mono.delay(properties.getSlowResponseDelay()).map(ignored -> ResponseEntity.ok(response));
        }
        if ("server-error".equals(shipmentId)) return error(500, "LOGISTICS_SERVICE_FAILED", "物流服务模拟失败");
        ShipmentResponse shipment = shipments.get(shipmentId);
        return shipment == null ? error(404, "SHIPMENT_NOT_FOUND", "未找到指定运单") : Mono.just(ResponseEntity.ok(shipment));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "创建运单")
    public Mono<ResponseEntity<?>> createShipment(@Valid @RequestBody CreateShipmentRequest request) {
        if ("server-error".equals(request.orderId())) return error(500, "SHIPMENT_CREATE_FAILED", "运单创建模拟失败");
        if ("not-found".equals(request.orderId())) return error(404, "ORDER_NOT_FOUND", "未找到指定订单");
        if ("slow".equals(request.orderId())) {
            return Mono.delay(properties.getSlowResponseDelay()).map(ignored -> createNow(request));
        }
        return Mono.just(createNow(request));
    }

    @PatchMapping(path = "/{shipmentId}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "更新物流状态")
    public Mono<ResponseEntity<?>> updateStatus(@PathVariable String shipmentId,
            @Valid @RequestBody UpdateShipmentStatusRequest request) {
        if ("server-error".equals(shipmentId)) return error(500, "SHIPMENT_UPDATE_FAILED", "物流更新模拟失败");
        synchronized (shipments) {
            ShipmentResponse current = shipments.get(shipmentId);
            if (current == null) return error(404, "SHIPMENT_NOT_FOUND", "未找到指定运单");
            if (!TRANSITIONS.get(current.status()).contains(request.status())) {
                return error(409, "INVALID_SHIPMENT_TRANSITION", "物流状态不允许这样流转");
            }
            ShipmentResponse updated = new ShipmentResponse(current.id(), current.orderId(), current.carrier(),
                    current.recipientAddress(), request.status(), request.location());
            shipments.put(shipmentId, updated);
            return Mono.just(ResponseEntity.ok(updated));
        }
    }

    private synchronized ResponseEntity<?> createNow(CreateShipmentRequest request) {
        String id = "s-" + sequence.getAndIncrement();
        ShipmentResponse created = new ShipmentResponse(id, request.orderId(), request.carrier(),
                request.recipientAddress(), ShipmentStatus.CREATED, "待揽收");
        shipments.put(id, created);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    private Mono<ResponseEntity<?>> error(int status, String code, String message) {
        return Mono.just(ResponseEntity.status(status).body(new ErrorResponse(code, message)));
    }

    public enum ShipmentStatus { CREATED, PICKED_UP, IN_TRANSIT, DELIVERED }
    public record ShipmentResponse(String id, String orderId, String carrier, String recipientAddress,
            ShipmentStatus status, String location) {}
    public record CreateShipmentRequest(@NotBlank String orderId, @NotBlank String carrier,
            @NotBlank String recipientAddress) {}
    public record UpdateShipmentStatusRequest(@NotNull ShipmentStatus status, @NotBlank String location) {}
    public record ErrorResponse(String code, String message) {}
}
