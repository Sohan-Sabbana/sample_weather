package com.example.weather.tests;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.LogDetail;
import io.restassured.specification.RequestSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;

import java.util.UUID;

/**
 * Shared setup for MAT + Regression suites.
 *
 *  - Reads the API base URL from -Dapi.base.url (default http://localhost:8080).
 *  - Generates a fresh trace id PER TEST and forwards it to the API as X-Trace-Id.
 *  - Stamps the trace id into the test-side MDC too, so test logs AND server
 *    logs in Elasticsearch can be joined on the same traceId.
 *
 *  Pipeline stage is read from -Dtest.stage (mat|regression) so logs from the
 *  two suites are distinguishable in Kibana.
 */
public class BaseApiTest {

    private static final Logger log = LoggerFactory.getLogger(BaseApiTest.class);

    protected static final String STAGE = System.getProperty("test.stage", "test");
    protected static final String RUN_ID = System.getProperty("test.run.id",
            System.getenv().getOrDefault("BUILD_TAG", "local-" + UUID.randomUUID()));

    protected RequestSpecification spec;
    private String currentTraceId;

    @BeforeSuite(alwaysRun = true)
    public void configure() {
        String base = System.getProperty("api.base.url", "http://localhost:8080");
        RestAssured.baseURI = base;
        log.info("Test suite configured stage={} runId={} baseUri={}", STAGE, RUN_ID, base);
    }

    @BeforeMethod(alwaysRun = true)
    public void beforeEachTest(java.lang.reflect.Method method) {
        currentTraceId = UUID.randomUUID().toString();
        MDC.put("traceId", currentTraceId);
        MDC.put("pipelineStage", STAGE);
        MDC.put("testRunId", RUN_ID);
        MDC.put("testName", method.getName());
        var testResult = org.testng.Reporter.getCurrentTestResult();
        if (testResult != null) {
            testResult.setAttribute("traceId", currentTraceId);
        }

        spec = new RequestSpecBuilder()
                .addHeader("X-Trace-Id", currentTraceId)
                .addHeader("X-Pipeline-Stage", STAGE)
                .addHeader("X-Test-Name", method.getName())
                .setContentType("application/json")
                .log(LogDetail.METHOD)
                .log(LogDetail.URI)
                .build();

        log.info("BEGIN test={} traceId={} stage={}", method.getName(), currentTraceId, STAGE);
    }

    @AfterMethod(alwaysRun = true)
    public void afterEachTest(ITestResult result) {
        String outcome = switch (result.getStatus()) {
            case ITestResult.SUCCESS -> "PASS";
            case ITestResult.FAILURE -> "FAIL";
            case ITestResult.SKIP -> "SKIP";
            default -> "UNKNOWN";
        };
        if (result.getThrowable() != null) {
            log.error("END test={} outcome={} traceId={} error={}",
                    result.getMethod().getMethodName(), outcome, currentTraceId,
                    result.getThrowable().getMessage());
        } else {
            log.info("END test={} outcome={} traceId={}",
                    result.getMethod().getMethodName(), outcome, currentTraceId);
        }
        MDC.clear();
    }

    protected String currentTraceId() {
        return currentTraceId;
    }

    /** Opens a named sub-flow; logs and requests carry {@code X-Flow} for Kibana correlation. */
    protected Flow flow(String name) {
        return new Flow(name, spec);
    }
}
