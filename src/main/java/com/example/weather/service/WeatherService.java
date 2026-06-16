package com.example.weather.service;

import com.example.weather.exception.NotFoundException;
import com.example.weather.model.ForecastDay;
import com.example.weather.model.WeatherReading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);
    private static final List<String> CONDITIONS =
            List.of("Clear", "Partly cloudy", "Cloudy", "Light rain", "Thunderstorm", "Foggy", "Sunny");

    private final CityService cityService;
    private final ValidationClient validationClient;

    public WeatherService(CityService cityService, ValidationClient validationClient) {
        this.cityService = cityService;
        this.validationClient = validationClient;
    }

    public WeatherReading current(String cityName) {
        var city = cityService.findByName(cityName)
                .orElseThrow(() -> new NotFoundException("Unknown city: " + cityName));
        // Fan out to the downstream validation-service (MS-2). Trace + test headers
        // are forwarded automatically, so its pod logs correlate to this request.
        validationClient.validateCity(city.getName(), city.getCountryCode());
        Random r = seeded(cityName, LocalDate.now().toString());
        WeatherReading reading = WeatherReading.builder()
                .city(cityName)
                .temperatureCelsius(round(15 + r.nextDouble() * 20))
                .humidityPercent(round(40 + r.nextDouble() * 50))
                .windSpeedKmh(round(r.nextDouble() * 30))
                .conditions(CONDITIONS.get(r.nextInt(CONDITIONS.size())))
                .observedAt(Instant.now())
                .build();
        log.info("Current weather city={} temp={}C conditions='{}'",
                cityName, reading.getTemperatureCelsius(), reading.getConditions());
        return reading;
    }

    public List<ForecastDay> forecast(String cityName, int days) {
        cityService.findByName(cityName)
                .orElseThrow(() -> new NotFoundException("Unknown city: " + cityName));
        if (days < 1 || days > 14) {
            throw new IllegalArgumentException("days must be between 1 and 14");
        }
        List<ForecastDay> out = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate d = LocalDate.now().plusDays(i);
            Random r = seeded(cityName, d.toString());
            double min = round(10 + r.nextDouble() * 10);
            double max = round(min + 5 + r.nextDouble() * 10);
            out.add(ForecastDay.builder()
                    .date(d)
                    .minTempCelsius(min)
                    .maxTempCelsius(max)
                    .precipitationMm(round(r.nextDouble() * 8))
                    .conditions(CONDITIONS.get(r.nextInt(CONDITIONS.size())))
                    .build());
        }
        log.info("Forecast city={} days={}", cityName, days);
        return out;
    }

    public List<WeatherReading> history(String cityName, int days) {
        cityService.findByName(cityName)
                .orElseThrow(() -> new NotFoundException("Unknown city: " + cityName));
        if (days < 1 || days > 30) {
            throw new IllegalArgumentException("days must be between 1 and 30");
        }
        List<WeatherReading> out = new ArrayList<>();
        for (int i = days; i >= 1; i--) {
            LocalDate d = LocalDate.now().minusDays(i);
            Random r = seeded(cityName, d.toString());
            out.add(WeatherReading.builder()
                    .city(cityName)
                    .temperatureCelsius(round(15 + r.nextDouble() * 20))
                    .humidityPercent(round(40 + r.nextDouble() * 50))
                    .windSpeedKmh(round(r.nextDouble() * 30))
                    .conditions(CONDITIONS.get(r.nextInt(CONDITIONS.size())))
                    .observedAt(Instant.now().minus(i, ChronoUnit.DAYS))
                    .build());
        }
        log.info("History city={} days={}", cityName, days);
        return out;
    }

    private Random seeded(String city, String date) {
        return new Random((long) (city.toLowerCase().hashCode()) * 31 + date.hashCode());
    }

    private double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
