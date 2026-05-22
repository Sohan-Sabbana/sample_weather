package com.example.weather.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A single day's forecast")
public class ForecastDay {

    private LocalDate date;

    @Schema(example = "30.1")
    private Double maxTempCelsius;

    @Schema(example = "21.4")
    private Double minTempCelsius;

    @Schema(example = "0.2")
    private Double precipitationMm;

    @Schema(example = "Light showers")
    private String conditions;
}
