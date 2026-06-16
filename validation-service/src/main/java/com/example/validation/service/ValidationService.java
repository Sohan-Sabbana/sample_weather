package com.example.validation.service;

import com.example.validation.model.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stateless validation rules. Every check logs at INFO on success and WARN on
 * failure; because the MDC already carries the upstream traceId + testName (set
 * by {@code TraceContextFilter}), those WARN lines land in Elasticsearch tagged
 * with the same request chain the failing test triggered.
 */
@Service
public class ValidationService {

    private static final Logger log = LoggerFactory.getLogger(ValidationService.class);
    private static final String CHECKED_BY = "validation-service";
    // Matches weather-api's WeatherAlert severity allowable values.
    private static final Set<String> SEVERITIES = Set.of("INFO", "WARNING", "SEVERE");

    public ValidationResult validateCity(Map<String, Object> payload) {
        List<String> reasons = new ArrayList<>();
        String name = str(payload.get("name"));
        String countryCode = str(payload.get("countryCode"));

        if (name.isBlank()) {
            reasons.add("city name is blank");
        }
        if (countryCode.isBlank()) {
            reasons.add("countryCode is missing");
        } else if (countryCode.length() != 2) {
            reasons.add("countryCode must be a 2-letter ISO code, got '" + countryCode + "'");
        }

        if (reasons.isEmpty()) {
            log.info("City validation passed name='{}' country='{}'", name, countryCode);
            return ValidationResult.ok(CHECKED_BY);
        }
        log.warn("City validation FAILED name='{}' country='{}' reasons={}", name, countryCode, reasons);
        return ValidationResult.invalid(CHECKED_BY, reasons);
    }

    public ValidationResult validateAlert(Map<String, Object> payload) {
        List<String> reasons = new ArrayList<>();
        String city = str(payload.get("city"));
        String type = str(payload.get("type"));
        String severity = str(payload.get("severity")).toUpperCase();

        if (city.isBlank()) {
            reasons.add("alert city is blank");
        }
        if (type.isBlank()) {
            reasons.add("alert type is blank");
        }
        if (!SEVERITIES.contains(severity)) {
            reasons.add("severity must be one of " + SEVERITIES + ", got '" + severity + "'");
        }

        if (reasons.isEmpty()) {
            log.info("Alert validation passed city='{}' type='{}' severity='{}'", city, type, severity);
            return ValidationResult.ok(CHECKED_BY);
        }
        log.warn("Alert validation FAILED city='{}' type='{}' severity='{}' reasons={}",
                city, type, severity, reasons);
        return ValidationResult.invalid(CHECKED_BY, reasons);
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString().trim();
    }
}
