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
        sb.append("</head><body>");
        sb.append("<h2><b>Flow Execution Report</b></h2>");
        sb.append("<p>Stage: <b>").append(escape(stage)).append("</b> &nbsp;|&nbsp; ");
        sb.append("Run: <b>").append(escape(runId)).append("</b> &nbsp;|&nbsp; ");
        sb.append("Build: <b>").append(escape(build)).append("</b></p>");
        sb.append("<p>Summary: Total <b>").append(total).append("</b> &nbsp;|&nbsp; ");
        sb.append("<font color=\"#16a34a\"><b>Passed ").append(passed).append("</b></font> &nbsp;|&nbsp; ");
        sb.append("<font color=\"#dc2626\"><b>Failed ").append(failed).append("</b></font> &nbsp;|&nbsp; ");
        sb.append("<font color=\"#ca8a04\"><b>Skipped ").append(skipped).append("</b></font></p>");
        sb.append("<table border=\"1\" cellpadding=\"8\" cellspacing=\"0\" width=\"100%\">");
        sb.append("<thead><tr bgcolor=\"#d4d4d4\">");
        sb.append(th("Flow Name"));
        sb.append(th("Status"));
        sb.append(th("Trace ID"));
        sb.append(th("Total"));
        sb.append(th("Passed"));
        sb.append(th("Failed"));
        sb.append(th("Skipped"));
        sb.append(th("Logs"));
        sb.append("</tr></thead><tbody>");

        for (FlowRecord f : FLOWS) {
            String href = htmlHrefAttr(logHref(f.logPath));
            String kibanaUrl = htmlHrefAttr(kibanaDiscoverUrl(kibanaBase, f.traceId, build));
            String kqlHint = escape(kibanaKql(f.traceId, build));
            String rowBg = f.failed > 0 ? "#fef2f2" : (f.skipped > 0 ? "#fefce8" : "#ffffff");

            sb.append("<tr bgcolor=\"").append(rowBg).append("\">");
            sb.append("<td align=\"left\"><a href=\"").append(href).append("\"><b>");
            sb.append(escape(f.flowName)).append("</b></a></td>");
            sb.append(statusCell(f));
            sb.append("<td align=\"left\"><font size=\"2\">").append(escape(f.traceId)).append("</font></td>");
            sb.append("<td align=\"center\">1</td>");
            sb.append(resultCell(f.passed, "pass"));
            sb.append(resultCell(f.failed, "fail"));
            sb.append(resultCell(f.skipped, "skip"));
            sb.append("<td align=\"left\">");
            sb.append("<a href=\"").append(href).append("\">view logs</a>");
            if (!"unknown".equalsIgnoreCase(f.traceId)) {
                sb.append(" &nbsp;|&nbsp; <a href=\"").append(kibanaUrl);
                sb.append("\" target=\"_blank\" title=\"Kibana: ").append(kqlHint).append("\">Kibana</a>");
            }
            sb.append("</td></tr>");
        }

        sb.append("</tbody></table>");
        sb.append("<p><font size=\"2\">Flow name opens per-test log file. ");
        sb.append("Kibana: <a href=\"").append(htmlHrefAttr(kibanaBase + "/app/discover"));
        sb.append("\" target=\"_blank\">").append(escape(kibanaBase)).append("</a> ");
        sb.append("(KQL: <code>traceId:&lt;uuid&gt;</code>)</font></p>");
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

    /** Legacy HTML header cell — survives Jenkins HTML Publisher sanitization. */
    private static String th(String label) {
        return "<th align=\"center\"><b>" + label + "</b></th>";
    }

    private static String statusCell(FlowRecord f) {
        if (f.failed > 0) {
            return "<td bgcolor=\"#dc2626\" align=\"center\">"
                    + "<font color=\"#ffffff\"><b>FAILED</b></font></td>";
        }
        if (f.skipped > 0) {
            return "<td bgcolor=\"#ca8a04\" align=\"center\">"
                    + "<font color=\"#ffffff\"><b>SKIPPED</b></font></td>";
        }
        return "<td bgcolor=\"#16a34a\" align=\"center\">"
                + "<font color=\"#ffffff\"><b>PASSED</b></font></td>";
    }

    /**
     * Green / red / yellow blocks for Passed / Failed / Skipped counts (bgcolor + font).
     */
    private static String resultCell(int value, String kind) {
        if (value > 0) {
            return switch (kind) {
                case "pass" -> "<td bgcolor=\"#16a34a\" align=\"center\">"
                        + "<font color=\"#ffffff\" size=\"3\"><b>" + value + "</b></font></td>";
                case "fail" -> "<td bgcolor=\"#dc2626\" align=\"center\">"
                        + "<font color=\"#ffffff\" size=\"3\"><b>" + value + "</b></font></td>";
                case "skip" -> "<td bgcolor=\"#ca8a04\" align=\"center\">"
                        + "<font color=\"#ffffff\" size=\"3\"><b>" + value + "</b></font></td>";
                default -> "<td align=\"center\">" + value + "</td>";
            };
        }
        return "<td bgcolor=\"#eeeeee\" align=\"center\"><font color=\"#888888\">0</font></td>";
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
