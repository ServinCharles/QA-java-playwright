package com.example.ui.base;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Tracing;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * After each UI test: on failure, save a full-page screenshot and the Playwright trace
 * under target/failures and target/traces. On success, discard the trace so only
 * useful evidence is kept. Runs before the context is closed, so the page is still live.
 */
public class UiArtifactsExtension implements AfterEachCallback {

    private static final Path TARGET = Paths.get("target");

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        Optional<BaseUiTest> testInstance = extensionContext.getTestInstance()
                .filter(BaseUiTest.class::isInstance)
                .map(BaseUiTest.class::cast);
        if (testInstance.isEmpty()) {
            return;
        }
        BaseUiTest test = testInstance.get();
        String name = extensionContext.getTestMethod().map(Method::getName).orElse("test");

        if (extensionContext.getExecutionException().isPresent()) {
            Path failures = Files.createDirectories(TARGET.resolve("failures"));
            test.page().screenshot(new Page.ScreenshotOptions()
                    .setPath(failures.resolve(name + ".png"))
                    .setFullPage(true));

            Path traces = Files.createDirectories(TARGET.resolve("traces"));
            test.context().tracing().stop(new Tracing.StopOptions()
                    .setPath(traces.resolve(name + ".zip")));
        } else {
            test.context().tracing().stop();
        }
        test.context().close();
    }
}
