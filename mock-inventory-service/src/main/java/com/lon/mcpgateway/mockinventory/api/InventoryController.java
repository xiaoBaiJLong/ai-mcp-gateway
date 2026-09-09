package com.lon.mcpgateway.mockinventory.api;

import com.lon.mcpgateway.mockinventory.config.MockInventoryProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping(path = "/api/inventory", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "库存模拟服务", description = "用于验证库存查询、预留与释放")
public class InventoryController {
    private final MockInventoryProperties properties;
    private final Map<String, InventoryResponse> inventory = new ConcurrentHashMap<>();
    private final Map<String, ReservationResponse> reservations = new ConcurrentHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger(101);

    public InventoryController(MockInventoryProperties properties) {
        this.properties = properties;
        inventory.put(key("wh-100", "p-100"), new InventoryResponse("p-100", "wh-100", 100, 0));
        inventory.put(key("wh-100", "p-200"), new InventoryResponse("p-200", "wh-100", 200, 0));
    }

    @GetMapping("/{productId}")
    @Operation(summary = "查询商品库存")
    public Mono<ResponseEntity<?>> getInventory(@PathVariable String productId,
            @RequestParam(defaultValue = "wh-100") String warehouseId) {
        if ("slow".equals(productId)) {
            return Mono.delay(properties.getSlowResponseDelay())
                    .map(ignored -> ResponseEntity.ok(new InventoryResponse("slow", warehouseId, 1, 0)));
        }
        if ("server-error".equals(productId)) return error(500, "INVENTORY_SERVICE_FAILED", "库存服务模拟失败");
        InventoryResponse found = inventory.get(key(warehouseId, productId));
        return found == null ? error(404, "INVENTORY_NOT_FOUND", "未找到指定库存") : Mono.just(ResponseEntity.ok(found));
    }

    @PostMapping(path = "/reservations", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "预留库存")
    public Mono<ResponseEntity<?>> reserve(@Valid @RequestBody CreateReservationRequest request) {
        if ("server-error".equals(request.productId())) return error(500, "INVENTORY_RESERVE_FAILED", "库存预留模拟失败");
        if ("slow".equals(request.productId())) {
            return Mono.delay(properties.getSlowResponseDelay()).map(ignored -> reserveNow(request));
        }
        return Mono.just(reserveNow(request));
    }

    @PostMapping("/reservations/{reservationId}/release")
    @Operation(summary = "释放库存预留")
    public Mono<ResponseEntity<?>> release(@PathVariable String reservationId) {
        if ("server-error".equals(reservationId)) return error(500, "INVENTORY_RELEASE_FAILED", "库存释放模拟失败");
        synchronized (inventory) {
            ReservationResponse current = reservations.get(reservationId);
            if (current == null) return error(404, "RESERVATION_NOT_FOUND", "未找到指定库存预留");
            if (current.status() == ReservationStatus.RELEASED) {
                return error(409, "RESERVATION_ALREADY_RELEASED", "库存预留已经释放");
            }
            String inventoryKey = key(current.warehouseId(), current.productId());
            InventoryResponse stock = inventory.get(inventoryKey);
            inventory.put(inventoryKey, new InventoryResponse(stock.productId(), stock.warehouseId(),
                    stock.available() + current.quantity(), stock.reserved() - current.quantity()));
            ReservationResponse released = new ReservationResponse(current.id(), current.orderId(), current.productId(),
                    current.warehouseId(), current.quantity(), ReservationStatus.RELEASED);
            reservations.put(reservationId, released);
            return Mono.just(ResponseEntity.ok(released));
        }
    }

    private ResponseEntity<?> reserveNow(CreateReservationRequest request) {
        synchronized (inventory) {
            String inventoryKey = key(request.warehouseId(), request.productId());
            InventoryResponse stock = inventory.get(inventoryKey);
            if (stock == null) return ResponseEntity.status(404).body(new ErrorResponse("INVENTORY_NOT_FOUND", "未找到指定库存"));
            if (request.quantity() > stock.available()) {
                return ResponseEntity.status(409).body(new ErrorResponse("INSUFFICIENT_INVENTORY", "可用库存不足"));
            }
            inventory.put(inventoryKey, new InventoryResponse(stock.productId(), stock.warehouseId(),
                    stock.available() - request.quantity(), stock.reserved() + request.quantity()));
            String id = "r-" + sequence.getAndIncrement();
            ReservationResponse reservation = new ReservationResponse(id, request.orderId(), request.productId(),
                    request.warehouseId(), request.quantity(), ReservationStatus.ACTIVE);
            reservations.put(id, reservation);
            return ResponseEntity.status(HttpStatus.CREATED).body(reservation);
        }
    }

    private String key(String warehouseId, String productId) { return warehouseId + ":" + productId; }
    private Mono<ResponseEntity<?>> error(int status, String code, String message) {
        return Mono.just(ResponseEntity.status(status).body(new ErrorResponse(code, message)));
    }

    public enum ReservationStatus { ACTIVE, RELEASED }
    public record InventoryResponse(String productId, String warehouseId, int available, int reserved) {}
    public record CreateReservationRequest(@NotBlank String orderId, @NotBlank String productId,
            @NotBlank String warehouseId, @Min(1) int quantity) {}
    public record ReservationResponse(String id, String orderId, String productId, String warehouseId,
            int quantity, ReservationStatus status) {}
    public record ErrorResponse(String code, String message) {}
}
