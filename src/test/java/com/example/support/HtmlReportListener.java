package com.example.support;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestTag;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Writes a self-contained HTML report to target/test-report/index.html when the test run
 * ends, then opens it in the default browser. It runs inside the test JVM, so the report
 * appears whether the run passed or failed.
 *
 * Registered through META-INF/services/org.junit.platform.launcher.TestExecutionListener.
 * The browser is not opened when the CI environment variable is set or OPEN_REPORT=false.
 */
public class HtmlReportListener implements TestExecutionListener {

    private static final Path REPORT = Paths.get("target", "test-report", "index.html");
    private static final Path FAILURES_DIR = Paths.get("target", "failures");
    private static final AtomicBoolean OPENED = new AtomicBoolean(false);

    private final Map<String, Long> startTimes = new ConcurrentHashMap<>();
    private final List<Row> rows = Collections.synchronizedList(new ArrayList<>());
    private volatile TestPlan testPlan;
    private volatile long planStart = System.currentTimeMillis();

    private record Row(String suite, String name, String tags, String status,
                       long millis, String detail, String methodName) {
    }

    @Override
    public void testPlanExecutionStarted(TestPlan plan) {
        this.testPlan = plan;
        this.planStart = System.currentTimeMillis();
    }

    @Override
    public void executionStarted(TestIdentifier id) {
        startTimes.put(id.getUniqueId(), System.currentTimeMillis());
    }

    @Override
    public void executionSkipped(TestIdentifier id, String reason) {
        rows.add(row(id, "SKIPPED", 0, reason));
    }

    @Override
    public void executionFinished(TestIdentifier id, TestExecutionResult result) {
        boolean successful = result.getStatus() == TestExecutionResult.Status.SUCCESSFUL;
        // Containers (classes, parameterized methods) only matter when their setup or teardown fails.
        if (!id.isTest() && successful) {
            return;
        }
        long now = System.currentTimeMillis();
        long millis = now - startTimes.getOrDefault(id.getUniqueId(), now);
        String status;
        switch (result.getStatus()) {
            case SUCCESSFUL:
                status = "PASSED";
                break;
            case ABORTED:
                status = "SKIPPED";
                break;
            default:
                status = "FAILED";
        }
        String detail = result.getThrowable().map(HtmlReportListener::stackTrace).orElse("");
        rows.add(row(id, status, millis, detail));
    }

    @Override
    public void testPlanExecutionFinished(TestPlan plan) {
        if (rows.isEmpty()) {
            return;
        }
        try {
            Files.createDirectories(REPORT.getParent());
            Files.writeString(REPORT, buildHtml(System.currentTimeMillis() - planStart), StandardCharsets.UTF_8);
            System.out.println("[report] HTML report: " + REPORT.toAbsolutePath());
            openInBrowser();
        } catch (IOException | RuntimeException e) {
            System.err.println("[report] Could not write the HTML report: " + e);
        }
    }

    // ---- collecting -------------------------------------------------------------

    private Row row(TestIdentifier id, String status, long millis, String detail) {
        String method = id.getSource()
                .filter(MethodSource.class::isInstance)
                .map(source -> ((MethodSource) source).getMethodName())
                .orElse("");
        return new Row(suiteName(id), id.getDisplayName(), tags(id), status, millis,
                detail == null ? "" : detail, method);
    }

    /** Display name of the nearest enclosing test class. */
    private String suiteName(TestIdentifier id) {
        Optional<TestIdentifier> current = Optional.of(id);
        while (current.isPresent()) {
            TestIdentifier candidate = current.get();
            if (candidate.getSource().filter(ClassSource.class::isInstance).isPresent()) {
                return candidate.getDisplayName();
            }
            current = parentOf(candidate);
        }
        return "Other";
    }

    /** Tags on the test and on everything above it, e.g. "api" or "ui". */
    private String tags(TestIdentifier id) {
        TreeSet<String> names = new TreeSet<>();
        Optional<TestIdentifier> current = Optional.of(id);
        while (current.isPresent()) {
            for (TestTag tag : current.get().getTags()) {
                names.add(tag.getName());
            }
            current = parentOf(current.get());
        }
        return String.join(", ", names);
    }

    private Optional<TestIdentifier> parentOf(TestIdentifier id) {
        TestPlan plan = testPlan;
        if (plan == null) {
            return Optional.empty();
        }
        try {
            return plan.getParent(id);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static String stackTrace(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    // ---- opening ----------------------------------------------------------------

    private void openInBrowser() {
        boolean wanted = System.getenv("CI") == null
                && Boolean.parseBoolean(Config.get("OPEN_REPORT", "true"));
        if (!wanted || !OPENED.compareAndSet(false, true)) {
            return;
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String file = REPORT.toAbsolutePath().toString();
        List<String> command;
        if (os.contains("mac")) {
            command = List.of("open", file);
        } else if (os.contains("win")) {
            command = List.of("cmd", "/c", "start", "", file);
        } else {
            command = List.of("xdg-open", file);
        }
        try {
            // Output is discarded so the child process cannot write into Surefire's channel.
            new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException e) {
            System.err.println("[report] Could not open the report automatically: " + e.getMessage());
        }
    }

    // ---- rendering --------------------------------------------------------------

    private String buildHtml(long totalMillis) {
        List<Row> snapshot;
        synchronized (rows) {
            snapshot = new ArrayList<>(rows);
        }
        long passed = snapshot.stream().filter(r -> r.status().equals("PASSED")).count();
        long failed = snapshot.stream().filter(r -> r.status().equals("FAILED")).count();
        long skipped = snapshot.stream().filter(r -> r.status().equals("SKIPPED")).count();

        Map<String, List<Row>> bySuite = new LinkedHashMap<>();
        for (Row row : snapshot) {
            bySuite.computeIfAbsent(row.suite(), key -> new ArrayList<>()).add(row);
        }

        String overall = failed > 0 ? "FAILED" : "PASSED";
        String when = LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss"));

        StringBuilder html = new StringBuilder();
        html.append("<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
                .append("<title>Test Report: ").append(overall).append("</title>\n")
                .append("<style>").append(CSS).append("</style>\n</head>\n<body>\n<main>\n");

        html.append("<header>\n<h1>Test Report <span class=\"pill ").append(overall.toLowerCase(Locale.ROOT))
                .append("\">").append(overall).append("</span></h1>\n")
                .append("<p class=\"meta\">Run finished ").append(escape(when))
                .append(" &middot; total time ").append(duration(totalMillis)).append("</p>\n</header>\n");

        html.append("<section class=\"summary\">\n")
                .append(tile("Total", snapshot.size(), "total"))
                .append(tile("Passed", passed, "passed"))
                .append(tile("Failed", failed, "failed"))
                .append(tile("Skipped", skipped, "skipped"))
                .append("</section>\n");

        for (Map.Entry<String, List<Row>> suite : bySuite.entrySet()) {
            html.append("<section class=\"suite\">\n<h2>").append(escape(suite.getKey())).append("</h2>\n")
                    .append("<table>\n<thead><tr><th>Test</th><th>Layer</th><th>Result</th>")
                    .append("<th class=\"num\">Time</th></tr></thead>\n<tbody>\n");
            for (Row row : suite.getValue()) {
                String css = row.status().toLowerCase(Locale.ROOT);
                html.append("<tr>\n<td>").append(escape(row.name()));
                if (!row.detail().isBlank()) {
                    html.append("<details").append(row.status().equals("FAILED") ? " open" : "")
                            .append("><summary>Details</summary><pre>")
                            .append(escape(row.detail())).append("</pre>");
                    html.append(evidenceLinks(row)).append("</details>");
                }
                html.append("</td>\n<td>").append(escape(row.tags().toUpperCase(Locale.ROOT))).append("</td>\n")
                        .append("<td><span class=\"pill ").append(css).append("\">")
                        .append(row.status()).append("</span></td>\n")
                        .append("<td class=\"num\">").append(duration(row.millis())).append("</td>\n</tr>\n");
            }
            html.append("</tbody>\n</table>\n</section>\n");
        }

        html.append("</main>\n</body>\n</html>\n");
        return html.toString();
    }

    /** Links to the screenshot and trace that UiArtifactsExtension saves for a failed UI test. */
    private String evidenceLinks(Row row) {
        if (!row.status().equals("FAILED") || row.methodName().isBlank()) {
            return "";
        }
        StringBuilder links = new StringBuilder();
        if (Files.exists(FAILURES_DIR.resolve(row.methodName() + ".png"))) {
            links.append("<p><a href=\"../failures/").append(escape(row.methodName()))
                    .append(".png\">Screenshot at failure</a></p>");
        }
        if (Files.exists(Paths.get("target", "traces", row.methodName() + ".zip"))) {
            links.append("<p>Playwright trace: <code>target/traces/").append(escape(row.methodName()))
                    .append(".zip</code></p>");
        }
        return links.toString();
    }

    private static String tile(String label, long value, String css) {
        return "<div class=\"tile " + css + "\"><span class=\"value\">" + value
                + "</span><span class=\"label\">" + label + "</span></div>\n";
    }

    private static String duration(long millis) {
        if (millis < 1000) {
            return millis + " ms";
        }
        return String.format(Locale.ROOT, "%.2f s", millis / 1000.0);
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static final String CSS = """
            :root { --bg:#f6f7f9; --card:#ffffff; --text:#1c2330; --muted:#5f6b7a; --line:#e3e7ec;
                    --pass:#1a7f4b; --pass-bg:#e3f5ea; --fail:#c0262d; --fail-bg:#fde8e8;
                    --skip:#8a6100; --skip-bg:#fdf1cf; }
            @media (prefers-color-scheme: dark) {
              :root { --bg:#12161c; --card:#1b212a; --text:#e8ecf1; --muted:#9aa6b4; --line:#2b3440;
                      --pass:#5fd38d; --pass-bg:#16382a; --fail:#ff8a8a; --fail-bg:#4a1d20;
                      --skip:#f0c75e; --skip-bg:#40340f; }
            }
            * { box-sizing: border-box; }
            body { margin:0; background:var(--bg); color:var(--text);
                   font:15px/1.5 -apple-system, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; }
            main { max-width:1000px; margin:0 auto; padding:32px 16px 48px; }
            h1 { font-size:26px; margin:0 0 4px; display:flex; align-items:center; gap:12px; flex-wrap:wrap; }
            h2 { font-size:17px; margin:0 0 12px; }
            .meta { color:var(--muted); margin:0 0 24px; }
            .summary { display:grid; grid-template-columns:repeat(auto-fit, minmax(140px, 1fr)); gap:12px; margin-bottom:24px; }
            .tile { background:var(--card); border:1px solid var(--line); border-radius:10px; padding:16px; }
            .tile .value { display:block; font-size:30px; font-weight:700; line-height:1.1; }
            .tile .label { color:var(--muted); font-size:13px; }
            .tile.passed .value { color:var(--pass); }
            .tile.failed .value { color:var(--fail); }
            .tile.skipped .value { color:var(--skip); }
            .suite { background:var(--card); border:1px solid var(--line); border-radius:10px; padding:16px; margin-bottom:16px; overflow-x:auto; }
            table { width:100%; border-collapse:collapse; }
            th { text-align:left; font-size:12px; text-transform:uppercase; letter-spacing:.04em; color:var(--muted); padding:8px; border-bottom:1px solid var(--line); }
            td { padding:10px 8px; border-bottom:1px solid var(--line); vertical-align:top; }
            tr:last-child td { border-bottom:none; }
            .num { text-align:right; white-space:nowrap; font-variant-numeric:tabular-nums; }
            .pill { display:inline-block; padding:2px 10px; border-radius:999px; font-size:12px; font-weight:700; letter-spacing:.03em; }
            .pill.passed { color:var(--pass); background:var(--pass-bg); }
            .pill.failed { color:var(--fail); background:var(--fail-bg); }
            .pill.skipped { color:var(--skip); background:var(--skip-bg); }
            details { margin-top:8px; }
            summary { cursor:pointer; color:var(--muted); font-size:13px; }
            pre { background:var(--bg); border:1px solid var(--line); border-radius:8px; padding:12px; overflow-x:auto; font-size:12px; line-height:1.45; white-space:pre-wrap; word-break:break-word; }
            a { color:inherit; }
            """;
}
