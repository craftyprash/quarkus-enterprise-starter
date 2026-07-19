package com.starter.applicant;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasKey;
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
                        .post("/api/v1/applicants")
                        .then()
                        .statusCode(201)
                        .body("name", equalTo("Jane Doe"))
                        .body("id", notNullValue())
                        .extract()
                        .jsonPath()
                        .getLong("id");

        given().when()
                .get("/api/v1/applicants/" + id)
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
                .post("/api/v1/applicants")
                .then()
                .statusCode(201);

        given().contentType("application/json")
                .body(body)
                .post("/api/v1/applicants")
                .then()
                .statusCode(409);
    }

    @Test
    void listReturnsPaginatedShapeWithoutEnvelope() {
        given().contentType("application/json")
                .body(
                        """
                        {"name": "List User", "email": "list@example.com"}
                        """)
                .post("/api/v1/applicants")
                .then()
                .statusCode(201);

        given().when()
                .get("/api/v1/applicants?page=1&size=5&sort=name&order=asc")
                .then()
                .statusCode(200)
                .body("content", notNullValue())
                .body("page", equalTo(1))
                .body("size", equalTo(5))
                .body("$", hasKey("totalElements"))
                .body("$", hasKey("totalPages"));
    }

    @Test
    void invalidSortParamReturnsFieldError() {
        // Programmatic validation (Validator) of a loose query param -> 400 with field-level error.
        given().when()
                .get("/api/v1/applicants?sort=name&order=sideways")
                .then()
                .statusCode(400)
                .body("status", equalTo("error"))
                .body("errors.field", hasItems("order"));
    }

    @Test
    void invalidBodyReturnsFieldLevelErrors() {
        given().contentType("application/json")
                .body(
                        """
                        {"name": "", "email": "not-an-email"}
                        """)
                .when()
                .post("/api/v1/applicants")
                .then()
                .statusCode(400)
                .body("status", equalTo("error"))
                .body("errors.field", hasItems("name", "email"));
    }
}
