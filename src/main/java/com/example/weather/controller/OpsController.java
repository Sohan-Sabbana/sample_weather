package com.example.weather.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "Ops", description = "Operational endpoints used by the Jenkins pipeline")
public class OpsController {

    private static final Logger log = LoggerFactory.getLogger(OpsController.class);

    @Value("${spring.application.name:weather-api}")
    private String appName;

    @Value("${app.version:1.0.0}")
    private String version;

    @GetMapping("/health")
    @Operation(summary = "Liveness/readiness probe used by deploy stage")
    public Map<String, Object> health() {
        log.info("Health check OK");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", "UP");
        m.put("timestamp", Instant.now().toString());
        m.put("service", appName);
        return m;
    }

    @GetMapping("/version")
    @Operation(summary = "Get the currently deployed version")
    public Map<String, String> version() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("service", appName);
        m.put("version", version);
        return m;
    }
}
