package com.example.validation.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Lightweight liveness/readiness endpoint mirroring weather-api's /api/health so
 * the Kubernetes probes use the same path across services.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    @GetMapping("/health")
    public Map<String, String> health() {
        log.info("Health check OK");
        return Map.of("status", "UP", "service", "validation-service");
    }
}
