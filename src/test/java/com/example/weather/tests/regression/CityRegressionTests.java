package com.example.weather.tests.regression;

import com.example.weather.tests.BaseApiTest;
import io.restassured.response.Response;
import org.testng.annotations.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.testng.Assert.assertNotNull;

public class CityRegressionTests extends BaseApiTest {

    @Test
    public void listCities() {
        given().spec(spec).when().get("/api/cities").then().statusCode(200);
    }

    @Test
    public void getSeededCityById() {
        given().spec(spec).when().get("/api/cities/1").then().statusCode(200)
                .body("id", org.hamcrest.Matchers.equalTo(1));
    }

    @Test
    public void getMissingCityReturns404() {
        given().spec(spec).when().get("/api/cities/99999").then().statusCode(404);
    }

    @Test
    public void createUpdateDeleteCity() {
        Map<String, Object> body = Map.of(
                "name", "Test-City-" + currentTraceId().substring(0, 6),
                "countryCode", "ZZ",
                "latitude", 0.0,
                "longitude", 0.0);

        Response created = given().spec(spec).body(body)
                .when().post("/api/cities")
                .then().statusCode(201).extract().response();
        Number id = created.path("id");
        assertNotNull(id, "Created city should have an id");

        Map<String, Object> updated = Map.of(
                "name", "Test-City-Updated",
                "countryCode", "ZZ",
                "latitude", 1.1,
                "longitude", 2.2);

        given().spec(spec).body(updated)
                .when().put("/api/cities/" + id)
                .then().statusCode(200)
                .body("name", org.hamcrest.Matchers.equalTo("Test-City-Updated"));

        given().spec(spec).when().delete("/api/cities/" + id).then().statusCode(204);
        given().spec(spec).when().get("/api/cities/" + id).then().statusCode(404);
    }

    @Test
    public void createCityValidationFails() {
        Map<String, Object> bad = Map.of("name", "", "countryCode", "");
        given().spec(spec).body(bad)
                .when().post("/api/cities")
                .then().statusCode(400);
    }
}
