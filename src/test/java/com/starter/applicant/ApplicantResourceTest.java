package com.starter.applicant;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ApplicantResourceTest {

    @Test
    void createAndFetch() {
        var body =
                """
                {"name": "Jane Doe", "email": "jane@example.com"}
                """;

        var id =
                given().contentType("application/json")
                        .body(body)
                        .when()
                        .post("/applicants")
                        .then()
                        .statusCode(201)
                        .body("name", equalTo("Jane Doe"))
                        .body("id", notNullValue())
                        .extract()
                        .jsonPath()
                        .getLong("id");

        given().when()
                .get("/applicants/" + id)
                .then()
                .statusCode(200)
                .body("email", equalTo("jane@example.com"));
    }

    @Test
    void duplicateEmailReturns409() {
        var body =
                """
                {"name": "Dup User", "email": "dup@example.com"}
                """;

        given().contentType("application/json")
                .body(body)
                .post("/applicants")
                .then()
                .statusCode(201);

        given().contentType("application/json")
                .body(body)
                .post("/applicants")
                .then()
                .statusCode(409);
    }
}
