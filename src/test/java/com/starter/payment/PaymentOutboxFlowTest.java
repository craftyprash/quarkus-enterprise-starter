package com.starter.payment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.starter.payment.internal.DisbursementProcessor;
import com.starter.payment.internal.NeftSettlementPoller;
import com.starter.payment.internal.PaymentRepo;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusTest
class PaymentOutboxFlowTest {

    @Inject DisbursementProcessor disbursementProcessor;
    @Inject NeftSettlementPoller neftSettlementPoller;
    @Inject PaymentRepo paymentRepo;

    private long createApplicant(String email) {
        return given().contentType("application/json")
                .body("{\"name\":\"Anchor Co\",\"email\":\"" + email + "\"}")
                .post("/api/v1/applicants")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getLong("id");
    }

    private long createAndDisburse(long applicantId, String anchor, String amount) {
        var id =
                given().contentType("application/json")
                        .body(
                                "{\"applicantId\":"
                                        + applicantId
                                        + ",\"anchorCode\":\""
                                        + anchor
                                        + "\",\"amount\":"
                                        + amount
                                        + "}")
                        .post("/api/v1/drawdowns")
                        .then()
                        .statusCode(201)
                        .extract()
                        .jsonPath()
                        .getLong("id");
        given().post("/api/v1/drawdowns/" + id + "/disbursement").then().statusCode(200);
        return id;
    }

    private String paymentStatus(long drawdownId) {
        return QuarkusTransaction.requiringNew()
                .call(() -> paymentRepo.findByDrawdownId(drawdownId).orElseThrow().status);
    }

    private long paymentId(long drawdownId) {
        return QuarkusTransaction.requiringNew()
                .call(() -> paymentRepo.findByDrawdownId(drawdownId).orElseThrow().id);
    }

    @Test
    void impsDisbursesImmediately() {
        var applicantId = createApplicant("pay-imps@example.com");
        var drawdownId = createAndDisburse(applicantId, "TATA", "50000.00"); // TATA -> IDFC -> IMPS

        assertEquals("INITIATED", paymentStatus(drawdownId));

        disbursementProcessor.processPending();

        assertEquals("DISBURSED", paymentStatus(drawdownId));
        given().get("/api/v1/payments/" + paymentId(drawdownId))
                .then()
                .statusCode(200)
                .body("status", equalTo("DISBURSED"))
                .body("transferMode", equalTo("IMPS"));
    }

    @Test
    void neftPollsThenSettles() {
        var applicantId = createApplicant("pay-neft@example.com");
        var drawdownId =
                createAndDisburse(applicantId, "INFOSYS", "500000.00"); // INFOSYS -> HDFC -> NEFT

        disbursementProcessor.processPending();
        assertEquals("POLLING", paymentStatus(drawdownId));

        neftSettlementPoller.pollSettlements();
        assertEquals("DISBURSED", paymentStatus(drawdownId));
    }

    @Test
    void disbursementIsIdempotentUnderRetry() {
        var applicantId = createApplicant("pay-idem@example.com");
        var drawdownId = createAndDisburse(applicantId, "TATA", "1000.00");

        // Processing twice must apply exactly once.
        disbursementProcessor.processPending();
        disbursementProcessor.processPending();

        assertEquals("DISBURSED", paymentStatus(drawdownId));
        long count =
                QuarkusTransaction.requiringNew()
                        .call(() -> paymentRepo.count("drawdownId", drawdownId));
        assertEquals(1L, count);
    }
}
