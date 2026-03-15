package com.starter.drawdown;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class DrawdownResourceTest {

    @Test
    void createDrawdownForApplicant() {
        var applicantId =
                given().contentType("application/json")
                        .body(
                                """
                {"name": "Draw User", "email": "draw@example.com"}
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
                {"applicantId": %d, "anchorCode": "TATA", "amount": 50000.00}
                """
                                        .formatted(applicantId))
                        .post("/drawdowns")
                        .then()
                        .statusCode(201)
                        .body("applicantName", equalTo("Draw User"))
                        .body("anchorCode", equalTo("TATA"))
                        .body("status", equalTo("PENDING"))
                        .body("id", notNullValue())
                        .extract()
                        .jsonPath()
                        .getLong("id");

        given().get("/drawdowns/" + drawdownId)
                .then()
                .statusCode(200)
                .body("applicantName", equalTo("Draw User"));
    }
}
