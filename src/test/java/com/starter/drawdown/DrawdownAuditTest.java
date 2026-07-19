package com.starter.drawdown;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.starter.common.query.QueryRepo;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/** Envers writes a versioned row to drawdown_aud on every change (regulated audit trail). */
@QuarkusTest
class DrawdownAuditTest {

    @Inject QueryRepo queryRepo;

    @Test
    void everyChangeIsAudited() {
        var applicantId =
                given().contentType("application/json")
                        .body("{\"name\":\"Audit Co\",\"email\":\"audit@example.com\"}")
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
                                        + ",\"anchorCode\":\"TATA\",\"amount\":50000.00}")
                        .post("/api/v1/drawdowns") // revision 1: status PENDING
                        .then()
                        .statusCode(201)
                        .extract()
                        .jsonPath()
                        .getLong("id");

        given().post("/api/v1/drawdowns/" + drawdownId + "/disbursement") // revision 2: DISBURSING
                .then()
                .statusCode(200);

        long revisions =
                QuarkusTransaction.requiringNew()
                        .call(
                                () ->
                                        queryRepo
                                                .sql(
                                                        "SELECT count(*) FROM drawdown_aud WHERE id = ?1",
                                                        drawdownId)
                                                .single(Number.class)
                                                .longValue());

        assertTrue(revisions >= 2, "expected >= 2 audit revisions, got " + revisions);
    }
}
