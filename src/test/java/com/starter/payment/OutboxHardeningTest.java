package com.starter.payment;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.starter.payment.internal.DisbursementProcessor;
import com.starter.payment.internal.OutboxRepo;
import com.starter.payment.internal.PaymentRepo;
import com.starter.payment.internal.PaymentService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Outbox is hardened: retry with a cap (dead-letter) and reclaim of leases left by crashed workers.
 */
@QuarkusTest
class OutboxHardeningTest {

    @Inject PaymentService paymentService;
    @Inject DisbursementProcessor disbursementProcessor;
    @Inject OutboxRepo outboxRepo;
    @Inject PaymentRepo paymentRepo;

    /**
     * @return [drawdownId, paymentId, eventId]
     */
    private long[] createAndDisburse(String email) {
        var applicantId =
                given().contentType("application/json")
                        .body("{\"name\":\"Anchor Co\",\"email\":\"" + email + "\"}")
                        .post("/api/v1/applicants")
                        .then()
                        .statusCode(201)
                        .extract()
                        .jsonPath()
                        .getLong("id");
        var drawdownId =
                given().contentType("application/json")
                        .body(
                                "{\"applicantId\":"
                                        + applicantId
                                        + ",\"anchorCode\":\"TATA\",\"amount\":1000.00}")
                        .post("/api/v1/drawdowns")
                        .then()
                        .statusCode(201)
                        .extract()
                        .jsonPath()
                        .getLong("id");
        given().post("/api/v1/drawdowns/" + drawdownId + "/disbursement").then().statusCode(200);

        long paymentId =
                QuarkusTransaction.requiringNew()
                        .call(() -> paymentRepo.findByDrawdownId(drawdownId).orElseThrow().id);
        long eventId =
                QuarkusTransaction.requiringNew()
                        .call(() -> outboxRepo.findByAggregateId(paymentId).orElseThrow().id);
        return new long[] {drawdownId, paymentId, eventId};
    }

    private String eventStatus(long eventId) {
        return QuarkusTransaction.requiringNew().call(() -> outboxRepo.findById(eventId).status);
    }

    private String paymentStatus(long drawdownId) {
        return QuarkusTransaction.requiringNew()
                .call(() -> paymentRepo.findByDrawdownId(drawdownId).orElseThrow().status);
    }

    @Test
    void deadLettersAfterMaxAttempts() {
        var ids = createAndDisburse("outbox-dead@example.com");
        long drawdownId = ids[0], paymentId = ids[1], eventId = ids[2];

        // Simulate MAX transient failures: claim then fail, repeatedly.
        for (int i = 0; i < 3; i++) {
            assertTrue(paymentService.claim(eventId).isPresent());
            paymentService.failDisbursement(eventId, paymentId);
        }

        assertEquals("DEAD", eventStatus(eventId));
        assertEquals("FAILED", paymentStatus(drawdownId));
        assertTrue(paymentService.claim(eventId).isEmpty(), "dead event must not be claimable");
    }

    @Test
    void expiredLeaseIsReclaimed() {
        var ids = createAndDisburse("outbox-lease@example.com");
        long drawdownId = ids[0], eventId = ids[2];

        // Worker claims the event then "crashes" — never finalises.
        assertTrue(paymentService.claim(eventId).isPresent());
        assertEquals("IN_PROGRESS", eventStatus(eventId));

        // Expire the lease → the stuck event becomes claimable again.
        QuarkusTransaction.requiringNew()
                .run(
                        () ->
                                outboxRepo.findById(eventId).lockedUntil =
                                        Instant.now().minusSeconds(120));

        // Processor reclaims and completes it.
        disbursementProcessor.processPending();
        assertEquals("DISBURSED", paymentStatus(drawdownId));
    }
}
