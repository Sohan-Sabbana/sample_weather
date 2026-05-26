package com.example.weather.tests;

import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestResult;
import org.testng.ISuite;
import org.testng.ISuiteListener;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * After each suite, writes a Flow Execution Report (HTML + JSON) under {@code LOG_DIR}.
 * Each TestNG test method is treated as one flow; the flow name links to a per-flow
 * log extract that Jenkins archives as a build artifact.
 */
public class FlowExecutionListener implements ISuiteListener, IInvokedMethodListener {

    private static final List<FlowRecord> FLOWS = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void beforeInvocation(IInvokedMethod method, ITestResult result) {
        // no-op
    }

    @Override
    public void afterInvocation(IInvokedMethod method, ITestResult result) {
        if (!method.isTestMethod()) {
            return;
        }
        String traceId = (String) result.getAttribute("traceId");
        if (traceId == null) {
            traceId = "unknown";
        }
        FLOWS.add(FlowRecord.from(result, traceId));
    }

    @Override
    public void onFinish(ISuite suite) {
        if (FLOWS.isEmpty()) {
            return;
        }
        try {
            writeReports();
        } catch (IOException e) {
            throw new RuntimeException("Failed to write flow execution report", e);
        } finally {
            FLOWS.clear();
        }
    }

    private void writeReports() throws IOException {
        String stage = System.getProperty("test.stage", "test");
        String logDir = System.getProperty("LOG_DIR", "logs");
        String runId = System.getProperty("test.run.id", "local");
        String build = System.getProperty("BUILD_NUMBER", "local");

        Path base = Paths.get(logDir);
        Path flowsDir = base.resolve("flows");
        Files.createDirectories(flowsDir);

        Path jsonLog = base.resolve("tests-" + stage + ".json");
        for (int i = 0; i < FLOWS.size(); i++) {
            FlowRecord flow = FLOWS.get(i);
            Path flowLog = flowsDir.resolve(flow.logFileName());
            extractTraceLog(jsonLog, flow.traceId(), flowLog);
            FLOWS.set(i, flow.withLogPath("flows/" + flow.logFileName()));
        }

        FLOWS.sort(Comparator.comparing(FlowRecord::flowName));

        Path jsonOut = base.resolve("flow-execution-report-" + stage + ".json");
        Path htmlOut = base.resolve("flow-execution-report-" + stage + ".html");
        Files.writeString(jsonOut, toJson(stage, runId, build), StandardCharsets.UTF_8);
        Files.writeString(htmlOut, toHtml(stage, runId, build), StandardCharsets.UTF_8);

        // Stable name for Jenkins publishHTML / artifact link
        Files.writeString(base.resolve("flow-execution-report.html"), Files.readString(htmlOut), StandardCharsets.UTF_8);
    }

    private static void extractTraceLog(Path jsonLog, String traceId, Path out) throws IOException {
        if (!Files.isRegularFile(jsonLog)) {
            Files.writeString(out, "No suite log file at " + jsonLog + "\n", StandardCharsets.UTF_8);
            return;
        }
        String needle = "\"traceId\":\"" + traceId + "\"";
        try (BufferedReader reader = Files.newBufferedReader(jsonLog, StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(needle)) {
                    sb.append(line).append('\n');
                }
            }
            if (sb.isEmpty()) {
                sb.append("No log lines matched traceId=").append(traceId).append('\n');
            }
            Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
        }
    }

    private static String toJson(String stage, String runId, String build) {
        int total = FLOWS.size();
        int passed = (int) FLOWS.stream().filter(f -> f.failed == 0 && f.skipped == 0).count();
        int failed = (int) FLOWS.stream().filter(f -> f.failed > 0).count();
        int skipped = (int) FLOWS.stream().filter(f -> f.skipped > 0).count();

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"stage\": \"").append(escape(stage)).append("\",\n");
        sb.append("  \"runId\": \"").append(escape(runId)).append("\",\n");
        sb.append("  \"build\": \"").append(escape(build)).append("\",\n");
        sb.append("  \"generatedAt\": \"").append(Instant.now()).append("\",\n");
        sb.append("  \"summary\": {\"totalTests\": ").append(total)
                .append(", \"passed\": ").append(passed)
                .append(", \"failed\": ").append(failed)
                .append(", \"skipped\": ").append(skipped).append("},\n");
        sb.append("  \"flows\": [\n");
        for (int i = 0; i < FLOWS.size(); i++) {
            FlowRecord f = FLOWS.get(i);
            sb.append("    {\n");
            sb.append("      \"flowName\": \"").append(escape(f.flowName)).append("\",\n");
            sb.append("      \"className\": \"").append(escape(f.className)).append("\",\n");
            sb.append("      \"traceId\": \"").append(escape(f.traceId)).append("\",\n");
            sb.append("      \"totalTests\": 1, \"passed\": ").append(f.passed)
                    .append(", \"failed\": ").append(f.failed)
                    .append(", \"skipped\": ").append(f.skipped).append(",\n");
            sb.append("      \"status\": \"").append(escape(f.status)).append("\",\n");
            sb.append("      \"logPath\": \"").append(escape(f.logPath)).append("\"\n");
            sb.append("    }");
            if (i < FLOWS.size() - 1) {
                sb.append(',');
            }
            sb.append('\n');
        }
        sb.append("  ]\n}\n");
        return sb.toString();
    }

    private static String toHtml(String stage, String runId, String build) {
        int total = FLOWS.size();
        int passed = (int) FLOWS.stream().filter(f -> f.failed == 0 && f.skipped == 0).count();
        int failed = (int) FLOWS.stream().filter(f -> f.failed > 0).count();
        int skipped = (int) FLOWS.stream().filter(f -> f.skipped > 0).count();

        String kibanaBase = System.getProperty("kibana.url", "http://localhost:5601");

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"/>");
        sb.append("<title>Flow Execution Report — ").append(escape(stage)).append("</title>");
        sb.append("<style>");
        sb.append("body{font-family:system-ui,sans-serif;margin:1.5rem;color:#1a1a1a}");
        sb.append("h1{font-size:1.4rem}table{border-collapse:collapse;width:100%;margin-top:1rem}");
        sb.append("th,td{border:1px solid #ccc;padding:.5rem .75rem;text-align:left}");
        sb.append("th{background:#f4f4f4}tr.fail td{background:#fff0f0}");
        sb.append("tr.skip td{background:#fffbe6}a{color:#0b5fff}");
        sb.append(".meta{color:#555;font-size:.9rem;margin-bottom:1rem}");
        sb.append(".pass{color:#0a7a2f}.fail{color:#b00020}.skip{color:#8a6d00}");
        sb.append("</style></head><body>");
        sb.append("<h1>Flow Execution Report</h1>");
        sb.append("<p class=\"meta\">Stage: <b>").append(escape(stage)).append("</b> · ");
        sb.append("Run: <b>").append(escape(runId)).append("</b> · Build: <b>").append(escape(build)).append("</b></p>");
        sb.append("<p class=\"meta\">Summary: ");
        sb.append("Total <b>").append(total).append("</b> · ");
        sb.append("<span class=\"pass\">Passed ").append(passed).append("</span> · ");
        sb.append("<span class=\"fail\">Failed ").append(failed).append("</span> · ");
        sb.append("<span class=\"skip\">Skipped ").append(skipped).append("</span></p>");
        sb.append("<table><thead><tr>");
        sb.append("<th>Flow Name</th><th>Total Tests</th><th>Passed</th><th>Failed</th><th>Skipped</th>");
        sb.append("<th>Trace</th></tr></thead><tbody>");

        for (FlowRecord f : FLOWS) {
            String rowClass = f.failed > 0 ? "fail" : (f.skipped > 0 ? "skip" : "");
            sb.append("<tr class=\"").append(rowClass).append("\">");
            sb.append("<td><a href=\"").append(escape(f.logPath)).append("\" title=\"Flow logs\">");
            sb.append(escape(f.flowName)).append("</a></td>");
            sb.append("<td>1</td><td>").append(f.passed).append("</td>");
            sb.append("<td>").append(f.failed).append("</td><td>").append(f.skipped).append("</td>");
            sb.append("<td><a href=\"").append(escape(f.logPath)).append("\">logs</a> · ");
            sb.append("<a href=\"").append(kibanaDiscoverUrl(kibanaBase, f.traceId));
            sb.append("\" target=\"_blank\" rel=\"noopener\">Kibana</a></td>");
            sb.append("</tr>");
        }

        sb.append("</tbody></table>");
        sb.append("<p class=\"meta\">Open a flow name to view JSON log lines for that test (same traceId in Elasticsearch).</p>");
        sb.append("</body></html>");
        return sb.toString();
    }

    private static String kibanaDiscoverUrl(String kibanaBase, String traceId) {
        // Discover deep-link (Kibana 8.x) filtered on traceId in test + server indices
        String q = "traceId:\"" + traceId + "\"";
        return kibanaBase + "/app/discover#/?_g=(filters:!(),refreshInterval:(pause:!t,value:0),time:(from:now-7d,to:now))"
                + "&_a=(columns:!(message,level,flow,testName),filters:!(),query:(language:kuery,query:'"
                + q.replace("'", "\\'") + "'),index:weather-logs-test-*)";
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private record FlowRecord(
            String flowName,
            String className,
            String traceId,
            String status,
            int passed,
            int failed,
            int skipped,
            String logPath
    ) {
        static FlowRecord from(ITestResult result, String traceId) {
            String flowName = result.getMethod().getMethodName();
            String className = result.getTestClass().getRealClass().getSimpleName();
            int passed = 0, failed = 0, skipped = 0;
            String status;
            switch (result.getStatus()) {
                case ITestResult.SUCCESS -> {
                    status = "PASSED";
                    passed = 1;
                }
                case ITestResult.FAILURE -> {
                    status = "FAILED";
                    failed = 1;
                }
                case ITestResult.SKIP -> {
                    status = "SKIPPED";
                    skipped = 1;
                }
                default -> {
                    status = "UNKNOWN";
                    failed = 1;
                }
            }
            return new FlowRecord(flowName, className, traceId, status, passed, failed, skipped, "");
        }

        String logFileName() {
            String shortId = traceId.length() > 8 ? traceId.substring(0, 8) : traceId;
            return flowName + "-" + shortId + ".log";
        }

        FlowRecord withLogPath(String path) {
            return new FlowRecord(flowName, className, traceId, status, passed, failed, skipped, path);
        }
    }
}
