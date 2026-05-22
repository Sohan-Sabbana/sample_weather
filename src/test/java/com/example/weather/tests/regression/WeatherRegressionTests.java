package com.example.weather.tests.regression;

import com.example.weather.tests.BaseApiTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static io.restassured.RestAssured.given;

public class WeatherRegressionTests extends BaseApiTest {

    @DataProvider(name = "cities")
    public Object[][] cities() {
        return new Object[][]{{"Bengaluru"}, {"London"}, {"Tokyo"}, {"New York"}};
    }

    @Test(dataProvider = "cities")
    public void currentWeatherForEachSeededCity(String city) {
        given().spec(spec).when().get("/api/weather/current/" + city)
                .then().statusCode(200)
                .body("city", org.hamcrest.Matchers.equalTo(city));
    }

    @Test
    public void currentWeatherUnknownCity() {
        given().spec(spec).when().get("/api/weather/current/Atlantis")
                .then().statusCode(404);
    }

    @Test
    public void forecastDefaultDays() {
        given().spec(spec).when().get("/api/weather/forecast/Bengaluru")
                .then().statusCode(200)
                .body("size()", org.hamcrest.Matchers.equalTo(5));
    }

    @Test
    public void forecastCustomDays() {
        given().spec(spec).when().get("/api/weather/forecast/London?days=10")
                .then().statusCode(200)
                .body("size()", org.hamcrest.Matchers.equalTo(10));
    }

    @Test
    public void forecastInvalidDays() {
        given().spec(spec).when().get("/api/weather/forecast/Tokyo?days=99")
                .then().statusCode(400);
    }

    @Test
    public void historyDefaultDays() {
        given().spec(spec).when().get("/api/weather/history/Tokyo")
                .then().statusCode(200)
                .body("size()", org.hamcrest.Matchers.equalTo(7));
    }

    @Test
    public void historyCustomDays() {
        given().spec(spec).when().get("/api/weather/history/New York?days=14")
                .then().statusCode(200)
                .body("size()", org.hamcrest.Matchers.equalTo(14));
    }
}
