package com.example.weather.tests.mat;

import com.example.weather.tests.BaseApiTest;
import io.restassured.response.Response;
import org.testng.annotations.Test;

import static io.restassured.RestAssured.given;
import static org.testng.Assert.*;

/**
 * Minimum Acceptance Tests. One quick happy-path call per endpoint.
 * If any of these fail the deployment is rejected.
 */
public class MatSmokeTests extends BaseApiTest {

    @Test(description = "MAT: /api/health returns UP")
    public void healthIsUp() {
        given().spec(spec)
                .when().get("/api/health")
                .then().statusCode(200)
                .body("status", org.hamcrest.Matchers.equalTo("UP"));
    }

    @Test(description = "MAT: /api/version returns service + version")
    public void versionExposed() {
        given().spec(spec)
                .when().get("/api/version")
                .then().statusCode(200)
                .body("service", org.hamcrest.Matchers.notNullValue())
                .body("version", org.hamcrest.Matchers.notNullValue());
    }

    @Test(description = "MAT: Swagger UI is reachable")
    public void swaggerUiReachable() {
        given().spec(spec)
                .when().get("/v3/api-docs")
                .then().statusCode(200)
                .body("openapi", org.hamcrest.Matchers.notNullValue());
    }

    @Test(description = "MAT: seeded cities are returned")
    public void listCitiesHasSeedData() {
        given().spec(spec)
                .when().get("/api/cities")
                .then().statusCode(200)
                .body("size()", org.hamcrest.Matchers.greaterThanOrEqualTo(4));
    }

    @Test(description = "MAT: current weather works for a seeded city")
    public void currentWeatherForSeededCity() {
        given().spec(spec)
                .when().get("/api/weather/current/Bengaluru")
                .then().statusCode(200)
                .body("city", org.hamcrest.Matchers.equalTo("Bengaluru"))
                .body("temperatureCelsius", org.hamcrest.Matchers.notNullValue());
    }

    @Test(description = "MAT: response carries back the trace id we sent")
    public void traceIdRoundTrip() {
        Response r = given().spec(spec)
                .when().get("/api/health");
        r.then().statusCode(200);
        assertEquals(r.getHeader("X-Trace-Id"), currentTraceId(),
                "Server must echo X-Trace-Id for log correlation");
    }
}
