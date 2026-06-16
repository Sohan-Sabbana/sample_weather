package com.example.validation.controller;

import com.example.validation.model.ValidationResult;
import com.example.validation.service.ValidationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Downstream validation endpoints (the MS-2 / MS-3 roles from the architecture).
 * weather-api calls these during normal reads/writes, forwarding the trace + test
 * headers so the request chain is correlatable across services.
 */
@RestController
@RequestMapping("/api/validate")
public class ValidationController {

    private final ValidationService validationService;

    public ValidationController(ValidationService validationService) {
        this.validationService = validationService;
    }

    @PostMapping("/city")
    public ValidationResult validateCity(@RequestBody(required = false) Map<String, Object> payload) {
        return validationService.validateCity(payload == null ? Map.of() : payload);
    }

    @PostMapping("/alert")
    public ValidationResult validateAlert(@RequestBody(required = false) Map<String, Object> payload) {
        return validationService.validateAlert(payload == null ? Map.of() : payload);
    }
}
