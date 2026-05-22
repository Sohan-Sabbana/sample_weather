package com.example.weather.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A weather observation for a city at a moment in time")
public class WeatherReading {

    @Schema(example = "Bengaluru")
    private String city;

    @Schema(example = "27.4")
    private Double temperatureCelsius;

    @Schema(example = "65.0")
    private Double humidityPercent;

    @Schema(example = "12.3")
    private Double windSpeedKmh;

    @Schema(example = "Partly cloudy")
    private String conditions;

    private Instant observedAt;
}
