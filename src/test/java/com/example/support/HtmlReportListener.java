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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Writes a self-contained HTML report to target/test-report/index.html when the test run
 * ends, then opens it in the default browser. It runs inside the test JVM, so the report
 * appears whether the run passed or failed.
 *
 * Retries: when Surefire reruns failed tests (test.retries), each rerun arrives as another
 * test plan in the same JVM. Results are keyed by test id, so a test that fails and then
 * passes is shown once as FLAKY, and one that keeps failing is shown once as FAILED.
 *
 * Parallel runs: results can arrive from several threads, so all shared state is thread-safe.
 *
 * Registered through META-INF/services/org.junit.platform.launcher.TestExecutionListener.
 * The browser is not opened when the CI environment variable is set or OPEN_REPORT=false.
 */
public class HtmlReportListener implements TestExecutionListener {

    private static final Path REPORT = Paths.get("target", "test-report", "index.html");
    private static final Path TARGET = Paths.get("target");

    // Static, so the results survive even if the launcher creates a new listener for a rerun.
    private static final Map<String, Row> ROWS = new LinkedHashMap<>();
    private static final Map<String, Long> START_TIMES = new ConcurrentHashMap<>();
    private static final AtomicLong RUN_START = new AtomicLong(0);
    private static final AtomicInteger PLANS_FINISHED = new AtomicInteger(0);
    private static final AtomicBoolean OPENED = new AtomicBoolean(false);

    private volatile TestPlan testPlan;

    private record Row(String suite, String name, String tags, String status, long millis,
                       String detail, String artifactName, int attempts) {
    }

    @Override
    public void testPlanExecutionStarted(TestPlan plan) {
        this.testPlan = plan;
        RUN_START.compareAndSet(0, System.currentTimeMillis());
    }

    @Override
    public void executionStarted(TestIdentifier id) {
        START_TIMES.put(id.getUniqueId(), System.currentTimeMillis());
    }

    @Override
    public void executionSkipped(TestIdentifier id, String reason) {
        record(id, "SKIPPED", 0, reason);
    }

    @Override
    public void executionFinished(TestIdentifier id, TestExecutionResult result) {
        boolean successful = result.getStatus() == TestExecutionResult.Status.SUCCESSFUL;
        // Containers (classes, parameterized methods) only matter when their setup or teardown
        // fails, or when they pass on a retry after failing.
        if (!id.isTest() && successful && !hasRow(id)) {
            return;
        }
        long now = System.currentTimeMillis();
        long millis = now - START_TIMES.getOrDefault(id.getUniqueId(), now);
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
        record(id, status, millis, detail);
    }

    @Override
    public void testPlanExecutionFinished(TestPlan plan) {
        int plansFinished = PLANS_FINISHED.incrementAndGet();
        List<Row> snapshot = snapshot();
        if (snapshot.isEmpty()) {
            return;
        }
        try {
            Files.createDirectories(REPORT.getParent());
            long totalMillis = System.currentTimeMillis() - RUN_START.get();
            Files.writeString(REPORT, buildHtml(snapshot, totalMillis), StandardCharsets.UTF_8);
            System.out.println("[report] HTML report: " + REPORT.toAbsolutePath());

            // With retries on, a run with failures is followed by a rerun. Wait for the last one
            // before opening the browser, so it never shows a half-finished result.
            boolean failures = snapshot.stream().anyMatch(row -> row.status().equals("FAILED"));
            boolean rerunExpected = failures && plansFinished <= configuredRetries();
            if (!rerunExpected) {
                openInBrowser();
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("[report] Could not write the HTML report: " + e);
        }
    }

    // ---- collecting -------------------------------------------------------------

    private static boolean hasRow(TestIdentifier id) {
        synchronized (ROWS) {
            return ROWS.containsKey(id.getUniqueId());
        }
    }

    private static List<Row> snapshot() {
        synchronized (ROWS) {
            return new ArrayList<>(ROWS.values());
        }
    }

    /** Stores a result. A second result for the same test id is a retry of that test. */
    private void record(TestIdentifier id, String status, long millis, String detail) {
        String safeDetail = detail == null ? "" : detail;
        synchronized (ROWS) {
            Row previous = ROWS.get(id.getUniqueId());
            int attempts = previous == null ? 1 : previous.attempts() + 1;
            String finalStatus = status;
            String finalDetail = safeDetail;
            boolean failedBefore = previous != null
                    && (previous.status().equals("FAILED") || previous.status().equals("FLAKY"));
            if (failedBefore && status.equals("PASSED")) {
                finalStatus = "FLAKY";
                finalDetail = previous.detail(); // keep the reason it failed the first time
            }
            ROWS.put(id.getUniqueId(), new Row(suiteName(id), id.getDisplayName(), tags(id),
                    finalStatus, millis, finalDetail, artifactName(id), attempts));
        }
    }

    /** Same name UiArtifactsExtension uses for the screenshot, trace and video of this test. */
    private static String artifactName(TestIdentifier id) {
        return id.getSource()
                .filter(MethodSource.class::isInstance)
                .map(MethodSource.class::cast)
                .map(source -> ArtifactNames.forTest(
                        source.getClassName(), source.getMethodName(), id.getUniqueId()))
                .orElse("");
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

    /** How many times Surefire will rerun failed tests (set by -Dtest.retries in the pom). */
    private static int configuredRetries() {
        int retries = 0;
        for (String key : List.of("test.retries", "surefire.rerunFailingTestsCount")) {
            try {
                retries = Math.max(retries, Integer.parseInt(System.getProperty(key, "0").trim()));
            } catch (NumberFormatException ignored) {
                // not a number: treat as no retries
            }
        }
        return retries;
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

    private String buildHtml(List<Row> rows, long totalMillis) {
        long passed = count(rows, "PASSED");
        long flaky = count(rows, "FLAKY");
        long failed = count(rows, "FAILED");
        long skipped = count(rows, "SKIPPED");

        Map<String, List<Row>> bySuite = new LinkedHashMap<>();
        for (Row row : rows) {
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
                .append(" &middot; total time ").append(duration(totalMillis))
                .append(browserNote(rows)).append("</p>\n</header>\n");

        html.append("<section class=\"summary\">\n")
                .append(tile("Total", rows.size(), "total"))
                .append(tile("Passed", passed, "passed"))
                .append(tile("Flaky", flaky, "flaky"))
                .append(tile("Failed", failed, "failed"))
                .append(tile("Skipped", skipped, "skipped"))
                .append("</section>\n");

        for (Map.Entry<String, List<Row>> suite : bySuite.entrySet()) {
            html.append("<section class=\"suite\">\n<h2>").append(escape(suite.getKey())).append("</h2>\n")
                    .append("<table>\n<thead><tr><th>Test</th><th>Layer</th><th>Result</th>")
                    .append("<th class=\"num\">Time</th></tr></thead>\n<tbody>\n");
            for (Row row : suite.getValue()) {
                String css = row.status().toLowerCase(Locale.ROOT);
                html.append("<tr>\n<td>").append(escape(row.name())).append(retryNote(row));
                if (!row.detail().isBlank()) {
                    String summary = row.status().equals("FLAKY") ? "First failure" : "Details";
                    html.append("<details").append(row.status().equals("FAILED") ? " open" : "")
                            .append("><summary>").append(summary).append("</summary><pre>")
                            .append(escape(row.detail())).append("</pre>")
                            .append(evidenceLinks(row)).append("</details>");
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

    private static long count(List<Row> rows, String status) {
        return rows.stream().filter(row -> row.status().equals(status)).count();
    }

    /** One line under the test name saying what the retries did. */
    private static String retryNote(Row row) {
        if (row.status().equals("FLAKY")) {
            return "<p class=\"note\">Failed, then passed on attempt " + row.attempts() + "</p>";
        }
        if (row.status().equals("FAILED") && row.attempts() > 1) {
            return "<p class=\"note\">Failed on all " + row.attempts() + " attempts</p>";
        }
        return "";
    }

    /** Names the browser in the header, but only when UI tests were part of the run. */
    private static String browserNote(List<Row> rows) {
        boolean uiTestsRan = rows.stream()
                .anyMatch(row -> List.of(row.tags().split(", ")).contains("ui"));
        if (!uiTestsRan) {
            return "";
        }
        return " &middot; browser " + escape(Config.get("BROWSER", "chromium").trim().toLowerCase(Locale.ROOT));
    }

    /** Links to the screenshot, video and trace that UiArtifactsExtension saved for a failed UI test. */
    private static String evidenceLinks(Row row) {
        boolean failedAtSomePoint = row.status().equals("FAILED") || row.status().equals("FLAKY");
        if (!failedAtSomePoint || row.artifactName().isBlank()) {
            return "";
        }
        String name = row.artifactName();
        StringBuilder links = new StringBuilder();
        if (Files.exists(TARGET.resolve("failures").resolve(name + ".png"))) {
            links.append("<p><a href=\"../failures/").append(escape(name))
                    .append(".png\">Screenshot at failure</a></p>");
        }
        if (Files.exists(TARGET.resolve("videos").resolve(name + ".webm"))) {
            links.append("<p><a href=\"../videos/").append(escape(name))
                    .append(".webm\">Video of the failed test</a></p>");
        }
        if (Files.exists(TARGET.resolve("traces").resolve(name + ".zip"))) {
            links.append("<p>Playwright trace: <code>target/traces/").append(escape(name))
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
                    --flaky:#8a6100; --flaky-bg:#fdf1cf; --skip:#55606e; --skip-bg:#e6e9ee; }
            @media (prefers-color-scheme: dark) {
              :root { --bg:#12161c; --card:#1b212a; --text:#e8ecf1; --muted:#9aa6b4; --line:#2b3440;
                      --pass:#5fd38d; --pass-bg:#16382a; --fail:#ff8a8a; --fail-bg:#4a1d20;
                      --flaky:#f0c75e; --flaky-bg:#40340f; --skip:#b3bdc9; --skip-bg:#2b3440; }
            }
            * { box-sizing: border-box; }
            body { margin:0; background:var(--bg); color:var(--text);
                   font:15px/1.5 -apple-system, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; }
            main { max-width:1000px; margin:0 auto; padding:32px 16px 48px; }
            h1 { font-size:26px; margin:0 0 4px; display:flex; align-items:center; gap:12px; flex-wrap:wrap; }
            h2 { font-size:17px; margin:0 0 12px; }
            .meta { color:var(--muted); margin:0 0 24px; }
            .note { color:var(--muted); font-size:13px; margin:2px 0 0; }
            .summary { display:grid; grid-template-columns:repeat(auto-fit, minmax(120px, 1fr)); gap:12px; margin-bottom:24px; }
            .tile { background:var(--card); border:1px solid var(--line); border-radius:10px; padding:16px; }
            .tile .value { display:block; font-size:30px; font-weight:700; line-height:1.1; }
            .tile .label { color:var(--muted); font-size:13px; }
            .tile.passed .value { color:var(--pass); }
            .tile.flaky .value { color:var(--flaky); }
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
            .pill.flaky { color:var(--flaky); background:var(--flaky-bg); }
            .pill.failed { color:var(--fail); background:var(--fail-bg); }
            .pill.skipped { color:var(--skip); background:var(--skip-bg); }
            details { margin-top:8px; }
            summary { cursor:pointer; color:var(--muted); font-size:13px; }
            pre { background:var(--bg); border:1px solid var(--line); border-radius:8px; padding:12px; overflow-x:auto; font-size:12px; line-height:1.45; white-space:pre-wrap; word-break:break-word; }
            a { color:inherit; }
            """;
}
