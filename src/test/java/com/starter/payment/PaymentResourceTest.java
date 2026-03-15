package com.starter.payment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class PaymentResourceTest {

    @Test
    void disburseDrawdownCreatesPayment() {
        var applicantId =
                given().contentType("application/json")
                        .body(
                                """
                {"name": "Pay User", "email": "pay@example.com"}
                """)
                        .post("/applicants")
                        .then()
                        .statusCode(201)
                        .extract()
                        .jsonPath()
                        .getLong("id");

        var drawdownId =
                given().contentType("application/json")
                        .body(
                                """
                {"applicantId": %d, "anchorCode": "TATA", "amount": 100000.00}
                """
                                        .formatted(applicantId))
                        .post("/drawdowns")
                        .then()
                        .statusCode(201)
                        .extract()
                        .jsonPath()
                        .getLong("id");

        // disburse the drawdown — should create a payment via outbox
        given().post("/drawdowns/" + drawdownId + "/disburse")
                .then()
                .statusCode(200)
                .body("status", equalTo("DISBURSING"));

        // payment should exist (created in same transaction as outbox event)
        given().get("/payments/1")
                .then()
                .statusCode(200)
                .body("drawdownId", equalTo((int) drawdownId))
                .body("bank", equalTo("IDFC"))
                .body("transferMode", equalTo("IMPS"))
                .body("status", equalTo("INITIATED"))
                .body("id", notNullValue());
    }
}
