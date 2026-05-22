package com.example.weather.controller;

import com.example.weather.model.ForecastDay;
import com.example.weather.model.WeatherReading;
import com.example.weather.service.WeatherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/weather")
@Tag(name = "Weather", description = "Current weather, forecast and history")
public class WeatherController {

    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @GetMapping("/current/{city}")
    @Operation(summary = "Get the current weather for a city")
    public WeatherReading current(@PathVariable String city) {
        return weatherService.current(city);
    }

    @GetMapping("/forecast/{city}")
    @Operation(summary = "Get the multi-day forecast for a city")
    public List<ForecastDay> forecast(@PathVariable String city,
                                      @RequestParam(defaultValue = "5") int days) {
        return weatherService.forecast(city, days);
    }

    @GetMapping("/history/{city}")
    @Operation(summary = "Get historical readings for a city")
    public List<WeatherReading> history(@PathVariable String city,
                                        @RequestParam(defaultValue = "7") int days) {
        return weatherService.history(city, days);
    }
}
