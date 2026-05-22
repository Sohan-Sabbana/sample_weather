package com.example.weather.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A weather alert for a city")
public class WeatherAlert {

    @Schema(example = "1")
    private Long id;

    @NotBlank
    @Schema(example = "Bengaluru")
    private String city;

    @NotBlank
    @Schema(example = "HEAVY_RAIN", allowableValues = {"HEAVY_RAIN", "HEATWAVE", "STORM", "SNOW", "FOG"})
    private String type;

    @NotBlank
    @Schema(example = "WARNING", allowableValues = {"INFO", "WARNING", "SEVERE"})
    private String severity;

    @Schema(example = "Heavy rainfall expected for next 6 hours")
    private String message;

    private Instant raisedAt;
}
