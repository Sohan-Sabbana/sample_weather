package com.example.weather.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A city tracked by the weather service")
public class City {

    @Schema(example = "1")
    private Long id;

    @NotBlank
    @Schema(example = "Bengaluru")
    private String name;

    @NotBlank
    @Schema(example = "IN")
    private String countryCode;

    @NotNull
    @Schema(example = "12.9716")
    private Double latitude;

    @NotNull
    @Schema(example = "77.5946")
    private Double longitude;
}
