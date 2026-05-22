package com.example.weather.controller;

import com.example.weather.model.City;
import com.example.weather.service.CityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cities")
@Tag(name = "Cities", description = "Manage cities tracked by the weather service")
public class CityController {

    private final CityService cityService;

    public CityController(CityService cityService) {
        this.cityService = cityService;
    }

    @GetMapping
    @Operation(summary = "List all cities")
    public List<City> list() {
        return cityService.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a city by id")
    public City get(@PathVariable Long id) {
        return cityService.findById(id);
    }

    @PostMapping
    @Operation(summary = "Add a new city")
    public ResponseEntity<City> create(@Valid @RequestBody City city) {
        city.setId(null);
        return ResponseEntity.status(201).body(cityService.save(city));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing city")
    public City update(@PathVariable Long id, @Valid @RequestBody City city) {
        return cityService.update(id, city);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a city")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        cityService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
