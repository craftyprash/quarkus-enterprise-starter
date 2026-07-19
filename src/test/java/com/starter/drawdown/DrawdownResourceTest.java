package com.starter.drawdown;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class DrawdownResourceTest {

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

    @Test
    void createDisburseThenRejectSecondDisburse() {
        var applicantId = createApplicant("dd1@example.com");

        var id =
                given().contentType("application/json")
                        .body(
                                "{\"applicantId\":"
                                        + applicantId
                                        + ",\"anchorCode\":\"TATA\",\"amount\":50000.00}")
                        .post("/api/v1/drawdowns")
                        .then()
                        .statusCode(201)
                        .body("status", equalTo("PENDING"))
                        .extract()
                        .jsonPath()
                        .getLong("id");

        given().post("/api/v1/drawdowns/" + id + "/disbursement")
                .then()
                .statusCode(200)
                .body("status", equalTo("DISBURSING"));

        // Already DISBURSING — not PENDING — so the transition is rejected.
        given().post("/api/v1/drawdowns/" + id + "/disbursement").then().statusCode(409);
    }

    @Test
    void createWithMissingApplicantReturns404() {
        given().contentType("application/json")
                .body("{\"applicantId\":999999,\"anchorCode\":\"TATA\",\"amount\":1000.00}")
                .post("/api/v1/drawdowns")
                .then()
                .statusCode(404);
    }

    @Test
    void negativeAmountReturns400() {
        var applicantId = createApplicant("dd2@example.com");
        given().contentType("application/json")
                .body("{\"applicantId\":" + applicantId + ",\"anchorCode\":\"TATA\",\"amount\":-5}")
                .post("/api/v1/drawdowns")
                .then()
                .statusCode(400);
    }
}
