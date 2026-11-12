package com.example.ui.base;

import com.example.support.ArtifactNames;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Tracing;
import com.microsoft.playwright.Video;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * Runs after each UI test and decides what evidence to keep.
 *
 * Failed test: saves a full-page screenshot (target/failures), the Playwright trace
 * (target/traces) and the video (target/videos).
 * Passed test: discards the trace and the video, so only useful evidence is kept.
 *
 * File names come from ArtifactNames, so each case of a data-driven test keeps its own files
 * and the HTML report can link to them. Evidence problems are logged and never fail a test.
 */
public class UiArtifactsExtension implements AfterEachCallback {

    private static final Path TARGET = Paths.get("target");

    @Override
    public void afterEach(ExtensionContext extensionContext) {
        Optional<BaseUiTest> testInstance = extensionContext.getTestInstance()
                .filter(BaseUiTest.class::isInstance)
                .map(BaseUiTest.class::cast);
        if (testInstance.isEmpty()) {
            return;
        }
        BrowserContext browserContext = testInstance.get().context();
        Page page = testInstance.get().page();
        if (browserContext == null) {
            return; // the browser context was never created, so there is nothing to collect
        }

        boolean failed = extensionContext.getExecutionException().isPresent();
        String name = ArtifactNames.forTest(
                extensionContext.getRequiredTestClass().getName(),
                extensionContext.getRequiredTestMethod().getName(),
                extensionContext.getUniqueId());

        // The video file is only complete once the context is closed, so note its path first.
        Path recordedVideo = videoPath(page);
        try {
            if (failed) {
                saveScreenshot(page, name);
                saveTrace(browserContext, name);
            } else {
                browserContext.tracing().stop();
            }
        } catch (RuntimeException e) {
            log("Could not finish the trace for " + name, e);
        } finally {
            browserContext.close();
        }
        keepOrDiscardVideo(recordedVideo, failed, name);
    }

    private static void saveScreenshot(Page page, String name) {
        if (page == null) {
            return;
        }
        try {
            Path failures = Files.createDirectories(TARGET.resolve("failures"));
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(failures.resolve(name + ".png"))
                    .setFullPage(true));
        } catch (IOException | RuntimeException e) {
            log("Could not save the screenshot for " + name, e);
        }
    }

    private static void saveTrace(BrowserContext browserContext, String name) {
        try {
            Path traces = Files.createDirectories(TARGET.resolve("traces"));
            browserContext.tracing().stop(new Tracing.StopOptions()
                    .setPath(traces.resolve(name + ".zip")));
        } catch (IOException e) {
            log("Could not save the trace for " + name, e);
        }
    }

    /** Where Playwright is recording this page's video, or null when recording is off. */
    private static Path videoPath(Page page) {
        if (page == null) {
            return null;
        }
        try {
            Video video = page.video();
            return video == null ? null : video.path();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void keepOrDiscardVideo(Path recordedVideo, boolean failed, String name) {
        if (recordedVideo == null) {
            return;
        }
        try {
            if (failed) {
                Path videos = Files.createDirectories(TARGET.resolve("videos"));
                Files.move(recordedVideo, videos.resolve(name + ".webm"), StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.deleteIfExists(recordedVideo);
            }
        } catch (IOException e) {
            log("Could not " + (failed ? "save" : "delete") + " the video for " + name, e);
        }
    }

    private static void log(String message, Exception e) {
        System.err.println("[evidence] " + message + ": " + e.getMessage());
    }
}
