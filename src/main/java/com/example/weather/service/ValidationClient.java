package com.example.weather.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Thin client for the downstream validation-service (MS-2/MS-3). The trace + test
 * headers are added automatically by the interceptor in {@code DownstreamClientConfig},
 * so callers just pass the payload.
 *
 * Calls are advisory: a transport failure or an "invalid" verdict is logged but
 * never breaks the weather request. The point is to produce correlated downstream
 * pod logs the analyzer can follow by traceId, not to gate the API.
 */
@Service
public class ValidationClient {

    private static final Logger log = LoggerFactory.getLogger(ValidationClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final boolean enabled;

    public ValidationClient(
            RestTemplate downstreamRestTemplate,
            @Value("${validation.service.url:http://validation-service:8081}") String baseUrl,
            @Value("${validation.service.enabled:true}") boolean enabled) {
        this.restTemplate = downstreamRestTemplate;
        this.baseUrl = baseUrl;
        this.enabled = enabled;
    }

    public void validateCity(String name, String countryCode) {
        call("/api/validate/city", Map.of(
                "name", name == null ? "" : name,
                "countryCode", countryCode == null ? "" : countryCode));
    }

    public void validateAlert(String city, String type, String severity) {
        call("/api/validate/alert", Map.of(
                "city", city == null ? "" : city,
                "type", type == null ? "" : type,
                "severity", severity == null ? "" : severity));
    }

    private void call(String path, Map<String, Object> payload) {
        if (!enabled) {
            return;
        }
        String url = baseUrl + path;
        try {
            log.info("Calling downstream validation {} payload={}", path, payload);
            restTemplate.postForObject(url, payload, Map.class);
        } catch (RestClientException ex) {
            // Downstream unavailable/slow: keep serving, but record it for diagnosis.
            log.warn("Downstream validation call to {} failed: {}", url, ex.getMessage());
        }
    }
}
