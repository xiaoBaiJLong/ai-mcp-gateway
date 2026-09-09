package com.lon.mcpgateway.mockpayment.api;

import com.lon.mcpgateway.mockpayment.config.MockPaymentProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
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
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping(path = "/api/payments", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "支付模拟服务", description = "用于验证支付查询、创建与退款")
public class PaymentController {
    private final MockPaymentProperties properties;
    private final Map<String, PaymentResponse> payments = new ConcurrentHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger(101);

    public PaymentController(MockPaymentProperties properties) {
        this.properties = properties;
        payments.put("pay-100", new PaymentResponse("pay-100", "o-100", new BigDecimal("398.00"),
                BigDecimal.ZERO, PaymentMethod.CARD, PaymentStatus.SUCCEEDED));
    }

    @GetMapping("/{paymentId}")
    @Operation(summary = "按支付 ID 查询支付")
    public Mono<ResponseEntity<?>> getPayment(@PathVariable String paymentId) {
        if ("slow".equals(paymentId)) {
            PaymentResponse response = new PaymentResponse("slow", "o-100", BigDecimal.ONE, BigDecimal.ZERO,
                    PaymentMethod.CARD, PaymentStatus.SUCCEEDED);
            return Mono.delay(properties.getSlowResponseDelay()).map(ignored -> ResponseEntity.ok(response));
        }
        if ("server-error".equals(paymentId)) return error(500, "PAYMENT_SERVICE_FAILED", "支付服务模拟失败");
        PaymentResponse payment = payments.get(paymentId);
        return payment == null ? error(404, "PAYMENT_NOT_FOUND", "未找到指定支付") : Mono.just(ResponseEntity.ok(payment));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "创建支付")
    public Mono<ResponseEntity<?>> createPayment(@Valid @RequestBody CreatePaymentRequest request) {
        if ("server-error".equals(request.orderId())) return error(500, "PAYMENT_CREATE_FAILED", "支付创建模拟失败");
        if ("not-found".equals(request.orderId())) return error(404, "ORDER_NOT_FOUND", "未找到指定订单");
        if ("slow".equals(request.orderId())) {
            return Mono.delay(properties.getSlowResponseDelay()).map(ignored -> createNow(request));
        }
        return Mono.just(createNow(request));
    }

    @PostMapping(path = "/{paymentId}/refunds", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "退款")
    public Mono<ResponseEntity<?>> refund(@PathVariable String paymentId, @Valid @RequestBody RefundRequest request) {
        if ("server-error".equals(paymentId)) return error(500, "PAYMENT_REFUND_FAILED", "退款模拟失败");
        synchronized (payments) {
            PaymentResponse current = payments.get(paymentId);
            if (current == null) return error(404, "PAYMENT_NOT_FOUND", "未找到指定支付");
            BigDecimal refunded = current.refundedAmount().add(request.amount());
            if (refunded.compareTo(current.amount()) > 0) {
                return error(409, "REFUND_EXCEEDS_PAYMENT", "退款金额超过可退款金额");
            }
            PaymentStatus status = refunded.compareTo(current.amount()) == 0
                    ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED;
            PaymentResponse updated = new PaymentResponse(current.id(), current.orderId(), current.amount(), refunded,
                    current.method(), status);
            payments.put(paymentId, updated);
            return Mono.just(ResponseEntity.ok(new RefundResponse(paymentId, request.amount(), request.reason(), status, refunded)));
        }
    }

    private synchronized ResponseEntity<?> createNow(CreatePaymentRequest request) {
        String id = "pay-" + sequence.getAndIncrement();
        PaymentResponse created = new PaymentResponse(id, request.orderId(), request.amount(), BigDecimal.ZERO,
                request.method(), PaymentStatus.SUCCEEDED);
        payments.put(id, created);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    private Mono<ResponseEntity<?>> error(int status, String code, String message) {
        return Mono.just(ResponseEntity.status(status).body(new ErrorResponse(code, message)));
    }

    public enum PaymentMethod { CARD, WALLET, BANK_TRANSFER }
    public enum PaymentStatus { SUCCEEDED, PARTIALLY_REFUNDED, REFUNDED }
    public record PaymentResponse(String id, String orderId, BigDecimal amount, BigDecimal refundedAmount,
            PaymentMethod method, PaymentStatus status) {}
    public record CreatePaymentRequest(@NotBlank String orderId, @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotNull PaymentMethod method) {}
    public record RefundRequest(@NotNull @DecimalMin("0.01") BigDecimal amount, @NotBlank String reason) {}
    public record RefundResponse(String paymentId, BigDecimal amount, String reason, PaymentStatus status,
            BigDecimal totalRefundedAmount) {}
    public record ErrorResponse(String code, String message) {}
}
