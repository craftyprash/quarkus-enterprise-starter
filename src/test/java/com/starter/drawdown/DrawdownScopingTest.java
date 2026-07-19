package com.starter.drawdown;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * Record-level scoping: a caller may only read drawdowns for anchors in the gateway-provided
 * X-Anchor-Scope. Demonstrates CLAUDE.md §5 — enforce ownership with the gateway identity.
 */
@QuarkusTest
class DrawdownScopingTest {

    private long createApplicant(String email) {
        return given().contentType("application/json")
                .body("{\"name\":\"Scope Co\",\"email\":\"" + email + "\"}")
                .post("/api/v1/applicants")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getLong("id");
    }

    private long createDrawdown(long applicantId, String anchor) {
        return given().contentType("application/json")
                .body(
                        "{\"applicantId\":"
                                + applicantId
                                + ",\"anchorCode\":\""
                                + anchor
                                + "\",\"amount\":1000.00}")
                .post("/api/v1/drawdowns")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getLong("id");
    }

    @Test
    void callerInScopeCanRead() {
        var id = createDrawdown(createApplicant("scope-ok@example.com"), "TATA");
        given().header("X-Anchor-Scope", "TATA,RELIANCE")
                .get("/api/v1/drawdowns/" + id)
                .then()
                .statusCode(200)
                .body("anchorCode", equalTo("TATA"));
    }

    @Test
    void callerOutOfScopeForbidden() {
        var id = createDrawdown(createApplicant("scope-wrong@example.com"), "TATA");
        given().header("X-Anchor-Scope", "WIPRO")
                .get("/api/v1/drawdowns/" + id)
                .then()
                .statusCode(403);
    }

    @Test
    void callerWithNoScopeForbidden() {
        var id = createDrawdown(createApplicant("scope-none@example.com"), "TATA");
        given().get("/api/v1/drawdowns/" + id).then().statusCode(403);
    }
}
