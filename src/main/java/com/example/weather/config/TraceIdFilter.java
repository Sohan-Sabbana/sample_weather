package com.example.weather.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Reads X-Trace-Id from the incoming request (TestNG suites set it per-test)
 * or generates a fresh one. The id is put into Logback MDC so every log line
 * emitted while handling the request carries it, which is what the Jenkins
 * CD log-analyzer keys on when searching Elasticsearch.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String SPAN_ID_HEADER = "X-Span-Id";
    public static final String STAGE_HEADER = "X-Pipeline-Stage";
    public static final String TEST_NAME_HEADER = "X-Test-Name";
    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_SPAN_ID = "spanId";
    public static final String MDC_STAGE = "pipelineStage";
    public static final String MDC_TEST_NAME = "testName";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String traceId = header(request, TRACE_ID_HEADER, () -> UUID.randomUUID().toString());
        String spanId  = header(request, SPAN_ID_HEADER,  () -> UUID.randomUUID().toString().substring(0, 8));
        String stage   = header(request, STAGE_HEADER, () -> "runtime");
        // Set per-test by the TestNG suites; lets pod logs be filtered by test name,
        // matching the testName the analyzer already reads from the test-side logs.
        String testName = header(request, TEST_NAME_HEADER, () -> null);

        MDC.put(MDC_TRACE_ID, traceId);
        MDC.put(MDC_SPAN_ID, spanId);
        MDC.put(MDC_STAGE, stage);
        if (testName != null) {
            MDC.put(MDC_TEST_NAME, testName);
        }

        response.setHeader(TRACE_ID_HEADER, traceId);
        response.setHeader(SPAN_ID_HEADER, spanId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_TRACE_ID);
            MDC.remove(MDC_SPAN_ID);
            MDC.remove(MDC_STAGE);
            MDC.remove(MDC_TEST_NAME);
        }
    }

    private String header(HttpServletRequest req, String name, java.util.function.Supplier<String> fallback) {
        String v = req.getHeader(name);
        return (v == null || v.isBlank()) ? fallback.get() : v;
    }
}
