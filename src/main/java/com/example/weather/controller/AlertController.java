package com.example.weather.controller;

import com.example.weather.model.WeatherAlert;
import com.example.weather.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
@Tag(name = "Alerts", description = "Create and inspect weather alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    @Operation(summary = "List all alerts")
    public List<WeatherAlert> list() {
        return alertService.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an alert by id")
    public WeatherAlert get(@PathVariable Long id) {
        return alertService.findById(id);
    }

    @PostMapping
    @Operation(summary = "Raise a new alert")
    public ResponseEntity<WeatherAlert> create(@Valid @RequestBody WeatherAlert alert) {
        return ResponseEntity.status(201).body(alertService.create(alert));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an alert")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        alertService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
