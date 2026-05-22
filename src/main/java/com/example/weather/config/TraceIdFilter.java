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
    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_SPAN_ID = "spanId";
    public static final String MDC_STAGE = "pipelineStage";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String traceId = header(request, TRACE_ID_HEADER, () -> UUID.randomUUID().toString());
        String spanId  = header(request, SPAN_ID_HEADER,  () -> UUID.randomUUID().toString().substring(0, 8));
        String stage   = header(request, "X-Pipeline-Stage", () -> "runtime");

        MDC.put(MDC_TRACE_ID, traceId);
        MDC.put(MDC_SPAN_ID, spanId);
        MDC.put(MDC_STAGE, stage);

        response.setHeader(TRACE_ID_HEADER, traceId);
        response.setHeader(SPAN_ID_HEADER, spanId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_TRACE_ID);
            MDC.remove(MDC_SPAN_ID);
            MDC.remove(MDC_STAGE);
        }
    }

    private String header(HttpServletRequest req, String name, java.util.function.Supplier<String> fallback) {
        String v = req.getHeader(name);
        return (v == null || v.isBlank()) ? fallback.get() : v;
    }
}
