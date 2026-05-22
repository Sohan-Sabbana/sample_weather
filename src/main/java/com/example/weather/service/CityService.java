package com.example.weather.service;

import com.example.weather.exception.NotFoundException;
import com.example.weather.model.City;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class CityService {

    private static final Logger log = LoggerFactory.getLogger(CityService.class);

    private final ConcurrentHashMap<Long, City> store = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong(0);

    @PostConstruct
    void seed() {
        save(City.builder().name("Bengaluru").countryCode("IN").latitude(12.9716).longitude(77.5946).build());
        save(City.builder().name("London").countryCode("GB").latitude(51.5072).longitude(-0.1276).build());
        save(City.builder().name("Tokyo").countryCode("JP").latitude(35.6762).longitude(139.6503).build());
        save(City.builder().name("New York").countryCode("US").latitude(40.7128).longitude(-74.0060).build());
        log.info("Seeded {} cities", store.size());
    }

    public List<City> findAll() {
        log.info("Listing all cities, count={}", store.size());
        return store.values().stream().sorted((a, b) -> Long.compare(a.getId(), b.getId())).toList();
    }

    public City findById(Long id) {
        City c = store.get(id);
        if (c == null) {
            log.warn("City not found id={}", id);
            throw new NotFoundException("City " + id + " not found");
        }
        return c;
    }

    public Optional<City> findByName(String name) {
        return store.values().stream().filter(c -> c.getName().equalsIgnoreCase(name)).findFirst();
    }

    public City save(City c) {
        if (c.getId() == null) {
            c.setId(ids.incrementAndGet());
        }
        store.put(c.getId(), c);
        log.info("Saved city id={} name={}", c.getId(), c.getName());
        return c;
    }

    public City update(Long id, City updated) {
        City existing = findById(id);
        existing.setName(updated.getName());
        existing.setCountryCode(updated.getCountryCode());
        existing.setLatitude(updated.getLatitude());
        existing.setLongitude(updated.getLongitude());
        log.info("Updated city id={}", id);
        return existing;
    }

    public void delete(Long id) {
        if (store.remove(id) == null) {
            throw new NotFoundException("City " + id + " not found");
        }
        log.info("Deleted city id={}", id);
    }
}
