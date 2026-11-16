package com.example.ui.base;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs axe-core, the accessibility rules engine, on the current page.
 *
 * The test fails only for violations of WCAG 2.0 and 2.1 Level A and AA rules with serious or
 * critical impact. Minor and moderate findings are not blocking, so they do not fail the build.
 * axe-core is loaded from cdnjs at a fixed version, so results do not change without a deliberate
 * version bump.
 */
public final class AccessibilityChecks {

    /** axe-core 4.10.0, bundled with the tests. The demo site's Content Security Policy blocks CDN scripts. */
    private static final String AXE_RESOURCE = "axe/axe.min.js";
    private static final String RUN_AXE =
            "async () => JSON.stringify(await axe.run(document, "
                    + "{ runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] } }))";
    private static final List<String> BLOCKING_IMPACTS = List.of("serious", "critical");
    private static final ObjectMapper JSON = new ObjectMapper();

    private AccessibilityChecks() {
    }

    /** Fails the test, listing every blocking violation, if the current page has any. */
    public static void assertNoBlockingViolations(Page page) {
        page.addScriptTag(new Page.AddScriptTagOptions().setContent(loadAxe()));
        String report = (String) page.evaluate(RUN_AXE);

        List<String> blocking = new ArrayList<>();
        for (JsonNode violation : violations(report)) {
            String impact = violation.path("impact").asText();
            if (BLOCKING_IMPACTS.contains(impact)) {
                blocking.add(violation.path("id").asText() + " (" + impact + ", "
                        + violation.path("nodes").size() + " element(s)): "
                        + violation.path("help").asText());
            }
        }
        Assertions.assertTrue(blocking.isEmpty(),
                "Serious or critical accessibility violations on " + page.url() + ":\n  - "
                        + String.join("\n  - ", blocking));
    }

    private static String loadAxe() {
        try (InputStream in = AccessibilityChecks.class.getClassLoader().getResourceAsStream(AXE_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource " + AXE_RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static JsonNode violations(String report) {
        try {
            return JSON.readTree(report).path("violations");
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("axe-core returned a result that is not JSON", e);
        }
    }
}
