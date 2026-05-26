package com.example.weather.tests;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.specification.RequestSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * A named step inside a test. Use with try-with-resources so the flow tag
 * is reliably pushed onto / popped off the MDC:
 *
 * <pre>{@code
 *   try (Flow f = flow("create-city")) {
 *       given().spec(f.spec)
 *              .body(payload)
 *              .when().post("/api/cities")
 *              .then().statusCode(201);
 *   }
 * }</pre>
 *
 * The flow name is also forwarded to the server via the {@code X-Flow}
 * header, so server pod logs carry the same {@code flow} field.
 * This is what gives you, in Kibana:
 *
 *   suite -> class -> method -> flow -> individual log lines
 */
public class Flow implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(Flow.class);

    public final RequestSpecification spec;
    private final String name;
    private final String previousFlow;
    private final long startNanos;

    Flow(String name, RequestSpecification base) {
        this.name = name;
        this.previousFlow = MDC.get("flow");
        MDC.put("flow", name);
        this.spec = new RequestSpecBuilder()
                .addRequestSpecification(base)
                .addHeader("X-Flow", name)
                .build();
        this.startNanos = System.nanoTime();
        log.info("FLOW BEGIN name={}", name);
    }

    @Override
    public void close() {
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
        log.info("FLOW END name={} durationMs={}", name, durationMs);
        if (previousFlow == null) {
            MDC.remove("flow");
        } else {
            MDC.put("flow", previousFlow);
        }
    }
}
