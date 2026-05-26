package com.example.weather.tests;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.OutputStreamAppender;
import org.slf4j.LoggerFactory;
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
import java.util.regex.Pattern;

/**
 * Writes a Flow Execution Report (HTML + JSON) under {@code LOG_DIR}.
 * Each TestNG test method is one flow; flow name links to extracted JSON logs.
 */
public class FlowExecutionListener implements ISuiteListener, IInvokedMethodListener {

    private static final Pattern TRACE_ID_JSON = Pattern.compile("\"traceId\"\\s*:\\s*\"([^\"]+)\"");
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
        String traceId = resolveTraceId(result);
        String flowName = result.getMethod().getMethodName();
        FlowRecord record = FlowRecord.from(result, traceId);

        try {
            flushJsonLogFile();
            Path base = Paths.get(System.getProperty("LOG_DIR", "logs"));
            Path flowsDir = base.resolve("flows");
            Files.createDirectories(flowsDir);
            Path flowLog = flowsDir.resolve(record.logFileName());
            String stage = System.getProperty("test.stage", "test");
            Path jsonLog = base.resolve("tests-" + stage + ".json");
            extractTraceLog(jsonLog, traceId, flowName, flowLog);
            FLOWS.add(record.withLogPath("flows/" + record.logFileName()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract flow log for " + flowName, e);
        }
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

    private static String resolveTraceId(ITestResult result) {
        String traceId = BaseApiTest.currentTraceIdForListener();
        if (traceId == null || traceId.isBlank()) {
            Object attr = result.getAttribute("traceId");
            if (attr instanceof String s && !s.isBlank()) {
                traceId = s;
            }
        }
        if (traceId == null || traceId.isBlank()) {
            traceId = "unknown";
        }
        return traceId;
    }

    private static void flushJsonLogFile() throws IOException {
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext context)) {
            return;
        }
        Logger root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        var appender = root.getAppender("JSON_FILE");
        if (appender instanceof OutputStreamAppender<?> streamAppender) {
            var os = streamAppender.getOutputStream();
            if (os != null) {
                os.flush();
            }
        }
    }

    private void writeReports() throws IOException {
        String stage = System.getProperty("test.stage", "test");
        String logDir = System.getProperty("LOG_DIR", "logs");
        String runId = System.getProperty("test.run.id", "local");
        String build = System.getProperty("BUILD_NUMBER", "local");

        FLOWS.sort(Comparator.comparing(FlowRecord::flowName));

        Path base = Paths.get(logDir);
        Path jsonOut = base.resolve("flow-execution-report-" + stage + ".json");
        Path htmlOut = base.resolve("flow-execution-report-" + stage + ".html");
        Files.writeString(jsonOut, toJson(stage, runId, build), StandardCharsets.UTF_8);
        Files.writeString(htmlOut, toHtml(stage, runId, build), StandardCharsets.UTF_8);
        Files.writeString(base.resolve("flow-execution-report.html"), Files.readString(htmlOut), StandardCharsets.UTF_8);
    }

    private static void extractTraceLog(Path jsonLog, String traceId, String testName, Path out)
            throws IOException {
        if (!Files.isRegularFile(jsonLog)) {
            Files.writeString(out,
                    "No suite log file at " + jsonLog + " (tests may not have written JSON yet).\n",
                    StandardCharsets.UTF_8);
            return;
        }

        boolean matchByTrace = traceId != null && !"unknown".equalsIgnoreCase(traceId);
        String traceNeedle = matchByTrace ? "\"traceId\":\"" + traceId + "\"" : null;
        String testNeedle = "\"testName\":\"" + testName + "\"";

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = Files.newBufferedReader(jsonLog, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                boolean match = false;
                if (matchByTrace && line.contains(traceNeedle)) {
                    match = true;
                } else if (!matchByTrace && line.contains(testNeedle)) {
                    match = true;
                }
                if (match) {
                    sb.append(formatLogLine(line)).append('\n');
                }
            }
        }

        if (sb.isEmpty()) {
            sb.append("# No lines matched\n");
            sb.append("traceId=").append(traceId).append('\n');
            sb.append("testName=").append(testName).append('\n');
            sb.append("logFile=").append(jsonLog).append('\n');
        }
        Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
    }

    /** Pretty-print one JSON log line for the flow log artifact. */
    private static String formatLogLine(String line) {
        var m = TRACE_ID_JSON.matcher(line);
        if (m.find()) {
            return line;
        }
        return line;
    }

    private static String logHref(String logPath) {
        String base = System.getProperty("flow.report.base", "").trim();
        if (base.isEmpty()) {
            return logPath;
        }
        if (!base.endsWith("/")) {
            base = base + "/";
        }
        return base + logPath;
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

        String kibanaBase = System.getProperty("kibana.url", "http://localhost:5601").replaceAll("/$", "");

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"/>");
        sb.append("<title>Flow Execution Report — ").append(escape(stage)).append("</title>");
        sb.append("<style>");
        sb.append(CSS);
        sb.append("</style></head><body>");
        sb.append("<h1>Flow Execution Report</h1>");
        sb.append("<p class=\"meta\">Stage: <b>").append(escape(stage)).append("</b> · ");
        sb.append("Run: <b>").append(escape(runId)).append("</b> · Build: <b>").append(escape(build)).append("</b></p>");
        sb.append("<p class=\"meta\">Summary: ");
        sb.append("Total <b>").append(total).append("</b> · ");
        sb.append("<span class=\"pass\">Passed ").append(passed).append("</span> · ");
        sb.append("<span class=\"fail\">Failed ").append(failed).append("</span> · ");
        sb.append("<span class=\"skip\">Skipped ").append(skipped).append("</span></p>");
        sb.append("<table class=\"flow-table\"><thead><tr>");
        sb.append("<th>Flow Name</th><th>Status</th><th>Trace ID</th><th>Total</th>");
        sb.append("<th>Passed</th><th>Failed</th><th>Skipped</th><th>Logs</th></tr></thead><tbody>");

        for (FlowRecord f : FLOWS) {
            String rowClass = f.failed > 0 ? "fail" : (f.skipped > 0 ? "skip" : "pass");
            String href = htmlHrefAttr(logHref(f.logPath));
            String kibanaUrl = htmlHrefAttr(kibanaDiscoverUrl(kibanaBase, f.traceId, build));
            String kqlHint = escape(kibanaKql(f.traceId, build));

            sb.append("<tr class=\"").append(rowClass).append("\">");
            sb.append("<td class=\"flow-name\"><a class=\"flow-link\" href=\"").append(href).append("\">");
            sb.append(escape(f.flowName)).append("</a></td>");
            sb.append("<td>").append(statusBadge(f)).append("</td>");
            sb.append("<td class=\"trace\"><code>").append(escape(f.traceId)).append("</code></td>");
            sb.append("<td class=\"num\">1</td>");
            sb.append(resultCell(f.passed, "pass"));
            sb.append(resultCell(f.failed, "fail"));
            sb.append(resultCell(f.skipped, "skip"));
            sb.append("<td class=\"actions\">");
            sb.append("<a class=\"action-link\" href=\"").append(href).append("\">view logs</a>");
            if (!"unknown".equalsIgnoreCase(f.traceId)) {
                sb.append(" <a class=\"action-link kibana-link\" href=\"").append(kibanaUrl);
                sb.append("\" target=\"_blank\" rel=\"noopener noreferrer\" title=\"Kibana: ").append(kqlHint);
                sb.append("\">Kibana</a>");
            }
            sb.append("</td></tr>");
        }

        sb.append("</tbody></table>");
        sb.append("<p class=\"meta\">Flow name → JSON log extract. Kibana opens Discover with KQL: ");
        sb.append("<code>traceId:&lt;uuid&gt;</code> (set default data view to <code>weather-logs-test-*</code> or ");
        sb.append("<code>weather-logs-*</code>). Kibana URL: <code>").append(escape(kibanaBase)).append("</code></p>");
        sb.append("</body></html>");
        return sb.toString();
    }

    /**
     * Kibana 8 Discover deep link. KQL uses unquoted values (no {@code "} in the URL) so the HTML
     * {@code href} attribute is not truncated — that was why the link looked dead before.
     */
    private static String kibanaDiscoverUrl(String kibanaBase, String traceId, String build) {
        String kql = kibanaKql(traceId, build);
        return kibanaBase + "/app/discover#/?_g=(time:(from:now-7d,to:now))"
                + "&_a=(query:(language:kuery,query:'" + kql + "'))";
    }

    private static String kibanaKql(String traceId, String build) {
        StringBuilder kql = new StringBuilder();
        if (build != null && !build.isBlank() && !"local".equals(build)) {
            kql.append("build:").append(build).append(" AND ");
        }
        kql.append("traceId:").append(traceId);
        return kql.toString();
    }

    /** HTML attribute value for href — encode & only; do NOT use text escape on URLs. */
    private static String htmlHrefAttr(String url) {
        return url.replace("&", "&amp;");
    }

    private static String statusBadge(FlowRecord f) {
        if (f.failed > 0) {
            return "<span class=\"badge badge-fail\">FAILED</span>";
        }
        if (f.skipped > 0) {
            return "<span class=\"badge badge-skip\">SKIPPED</span>";
        }
        return "<span class=\"badge badge-pass\">PASSED</span>";
    }

    private static String resultCell(int value, String kind) {
        if (value > 0) {
            return "<td class=\"result-block block-" + kind + "\">" + value + "</td>";
        }
        return "<td class=\"result-block block-empty\">0</td>";
    }

    private static final String CSS = """
            body{font-family:Segoe UI,system-ui,sans-serif;margin:1.5rem 2rem;color:#1a1a1a;background:#fafafa}
            h1{font-size:1.5rem;border-bottom:3px solid #222;padding-bottom:.5rem;margin-bottom:.5rem}
            .flow-table{border-collapse:collapse;width:100%;margin-top:1rem;border:3px solid #222;box-shadow:0 2px 8px rgba(0,0,0,.12)}
            .flow-table th,.flow-table td{border:2px solid #444;padding:.65rem .9rem;text-align:center;vertical-align:middle}
            .flow-table th{background:#d4d4d4;font-weight:700;color:#111}
            .flow-table td.flow-name,.flow-table td.trace,.flow-table td.actions{text-align:left}
            .flow-table tr.pass td.flow-name{background:#f0fdf4}
            .flow-table tr.fail td.flow-name{background:#fef2f2}
            .flow-table tr.skip td.flow-name{background:#fefce8}
            .result-block{font-weight:700;font-size:1.05rem;min-width:3rem}
            .block-pass{background:#16a34a!important;color:#fff!important;border:2px solid #14532d!important}
            .block-fail{background:#dc2626!important;color:#fff!important;border:2px solid #7f1d1d!important}
            .block-skip{background:#ca8a04!important;color:#fff!important;border:2px solid #713f12!important}
            .block-empty{background:#f3f4f6;color:#9ca3af;border:2px solid #d1d5db!important}
            .badge{display:inline-block;padding:.25rem .65rem;border-radius:4px;font-size:.75rem;font-weight:700;border:2px solid}
            .badge-pass{background:#16a34a;color:#fff;border-color:#14532d}
            .badge-fail{background:#dc2626;color:#fff;border-color:#7f1d1d}
            .badge-skip{background:#ca8a04;color:#fff;border-color:#713f12}
            .flow-link{font-weight:600;color:#1d4ed8;text-decoration:underline}
            .action-link{color:#1d4ed8;text-decoration:underline;margin-right:.5rem}
            .kibana-link{color:#7c3aed;font-weight:600}
            .trace code{font-size:.78rem;word-break:break-all;background:#f3f4f6;padding:.15rem .35rem;border:1px solid #ccc}
            .meta{color:#444;font-size:.9rem;margin:.75rem 0}
            .pass{color:#16a34a;font-weight:700}.fail{color:#dc2626;font-weight:700}.skip{color:#ca8a04;font-weight:700}
            """;

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
            String safeTrace = traceId.replaceAll("[^a-zA-Z0-9-]", "");
            if (safeTrace.length() > 8) {
                safeTrace = safeTrace.substring(0, 8);
            }
            if (safeTrace.isEmpty()) {
                safeTrace = "notrace";
            }
            return flowName + "-" + safeTrace + ".log";
        }

        FlowRecord withLogPath(String path) {
            return new FlowRecord(flowName, className, traceId, status, passed, failed, skipped, path);
        }
    }
}
