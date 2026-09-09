package com.lon.mcpgateway.mockpayment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.cloud.nacos.discovery.enabled=false", "spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848",
        "spring.cloud.nacos.discovery.namespace=test", "spring.cloud.nacos.discovery.group=DEFAULT_GROUP",
        "mock.payment.slow-response-delay=PT0.05S"
})
class MockPaymentServiceHttpTest {
    @Autowired private WebTestClient client;

    @Test
    void createsReadsAndRefundsPayment() {
        client.post().uri("/api/payments").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"orderId\":\"o-101\",\"amount\":100.00,\"method\":\"WALLET\"}")
                .exchange().expectStatus().isCreated().expectBody().jsonPath("$.id").isEqualTo("pay-101");
        client.post().uri("/api/payments/pay-101/refunds").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"amount\":40.00,\"reason\":\"演示退款\"}").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("PARTIALLY_REFUNDED");
        client.get().uri("/api/payments/pay-101").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.refundedAmount").isEqualTo(40.0);
        client.post().uri("/api/payments/pay-101/refunds").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"amount\":61.00,\"reason\":\"超额\"}").exchange().expectStatus().isEqualTo(409);
    }

    @Test
    void publishesOpenApiAndSupportsFailureScenarios() {
        client.get().uri("/v3/api-docs").exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.paths['/api/payments/{paymentId}'].get").exists()
                .jsonPath("$.paths['/api/payments'].post").exists()
                .jsonPath("$.paths['/api/payments/{paymentId}/refunds'].post").exists();
        client.get().uri("/api/payments/not-found").exchange().expectStatus().isNotFound();
        client.get().uri("/api/payments/server-error").exchange().expectStatus().isEqualTo(500);
    }
}
