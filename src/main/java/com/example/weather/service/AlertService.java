package com.example.weather.service;

import com.example.weather.exception.NotFoundException;
import com.example.weather.model.WeatherAlert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final ConcurrentHashMap<Long, WeatherAlert> store = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong(0);

    private final ValidationClient validationClient;

    public AlertService(ValidationClient validationClient) {
        this.validationClient = validationClient;
    }

    public List<WeatherAlert> findAll() {
        log.info("Listing alerts count={}", store.size());
        return store.values().stream().sorted((a, b) -> Long.compare(a.getId(), b.getId())).toList();
    }

    public WeatherAlert findById(Long id) {
        WeatherAlert a = store.get(id);
        if (a == null) {
            log.warn("Alert not found id={}", id);
            throw new NotFoundException("Alert " + id + " not found");
        }
        return a;
    }

    public WeatherAlert create(WeatherAlert a) {
        // Fan out to the downstream validation-service (MS-3); trace + test headers
        // are forwarded so its pod logs join this request chain in Elasticsearch.
        validationClient.validateAlert(a.getCity(), a.getType(), a.getSeverity());
        a.setId(ids.incrementAndGet());
        a.setRaisedAt(Instant.now());
        store.put(a.getId(), a);
        log.info("Created alert id={} city={} type={} severity={}",
                a.getId(), a.getCity(), a.getType(), a.getSeverity());
        return a;
    }

    public void delete(Long id) {
        if (store.remove(id) == null) {
            throw new NotFoundException("Alert " + id + " not found");
        }
        log.info("Deleted alert id={}", id);
    }
}
