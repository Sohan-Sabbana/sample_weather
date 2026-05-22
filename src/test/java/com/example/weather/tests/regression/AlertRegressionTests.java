package com.example.weather.tests.regression;

import com.example.weather.tests.BaseApiTest;
import io.restassured.response.Response;
import org.testng.annotations.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;

public class AlertRegressionTests extends BaseApiTest {

    @Test
    public void listAlertsInitiallyAllowsAny() {
        given().spec(spec).when().get("/api/alerts").then().statusCode(200);
    }

    @Test
    public void createListGetAndDeleteAlert() {
        Map<String, Object> alert = Map.of(
                "city", "Bengaluru",
                "type", "HEAVY_RAIN",
                "severity", "WARNING",
                "message", "Reg test alert " + currentTraceId().substring(0, 6));

        Response created = given().spec(spec).body(alert)
                .when().post("/api/alerts")
                .then().statusCode(201).extract().response();

        Number id = created.path("id");
        given().spec(spec).when().get("/api/alerts/" + id)
                .then().statusCode(200)
                .body("type", org.hamcrest.Matchers.equalTo("HEAVY_RAIN"));

        given().spec(spec).when().delete("/api/alerts/" + id).then().statusCode(204);
        given().spec(spec).when().get("/api/alerts/" + id).then().statusCode(404);
    }

    @Test
    public void createAlertValidationFails() {
        Map<String, Object> bad = Map.of("city", "", "type", "", "severity", "");
        given().spec(spec).body(bad)
                .when().post("/api/alerts")
                .then().statusCode(400);
    }

    @Test
    public void deleteMissingAlertReturns404() {
        given().spec(spec).when().delete("/api/alerts/99999").then().statusCode(404);
    }
}
