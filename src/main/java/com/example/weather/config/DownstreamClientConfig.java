package com.example.weather.config;

import org.slf4j.MDC;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Builds the {@link RestTemplate} weather-api uses to call downstream services.
 *
 * The interceptor copies the current request's correlation context out of the
 * Logback MDC (populated by {@link TraceIdFilter}) onto every outgoing call, so
 * the downstream service logs with the SAME traceId + testName. This is what makes
 * the request chain joinable across services in Elasticsearch.
 */
@Configuration
public class DownstreamClientConfig {

    @Bean
    public RestTemplate downstreamRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(3))
                .additionalInterceptors(traceForwardingInterceptor())
                .build();
    }

    private ClientHttpRequestInterceptor traceForwardingInterceptor() {
        return (request, body, execution) -> {
            forward(request, TraceIdFilter.TRACE_ID_HEADER, TraceIdFilter.MDC_TRACE_ID);
            forward(request, TraceIdFilter.SPAN_ID_HEADER, TraceIdFilter.MDC_SPAN_ID);
            forward(request, TraceIdFilter.STAGE_HEADER, TraceIdFilter.MDC_STAGE);
            forward(request, TraceIdFilter.TEST_NAME_HEADER, TraceIdFilter.MDC_TEST_NAME);
            return execution.execute(request, body);
        };
    }

    private void forward(org.springframework.http.HttpRequest request, String header, String mdcKey) {
        String value = MDC.get(mdcKey);
        if (value != null && !value.isBlank()) {
            request.getHeaders().set(header, value);
        }
    }
}
