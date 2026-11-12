package com.example.ui.base;

import com.example.support.Config;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Shared UI test lifecycle: one browser per test class, one isolated context per test.
 *
 * Playwright for Java is not thread-safe, so nothing here is static. Each test class gets its
 * own Playwright and browser (PER_CLASS lifecycle), which lets test classes run in parallel
 * without sharing any Playwright object.
 *
 * Settings: BROWSER (chromium, firefox, webkit), HEADLESS, SLOW_MO and VIDEO.
 * Screenshots, traces and videos for failed tests are handled by UiArtifactsExtension.
 */
@Tag("ui")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(UiArtifactsExtension.class)
public abstract class BaseUiTest {

    /** Videos are recorded here first. UiArtifactsExtension keeps or deletes them after each test. */
    static final Path VIDEO_RECORDING_DIR = Paths.get("target", "videos", "recording");

    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;

    @BeforeAll
    void launchBrowser() {
        playwright = Playwright.create();
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                .setHeadless(Config.headless());
        if (Config.slowMoMillis() > 0) {
            options.setSlowMo(Config.slowMoMillis());
        }
        browser = browserType(playwright).launch(options);
    }

    /** Picks the engine named by the BROWSER setting. */
    private static BrowserType browserType(Playwright playwright) {
        switch (Config.browser()) {
            case "firefox":
                return playwright.firefox();
            case "webkit":
                return playwright.webkit();
            default:
                return playwright.chromium();
        }
    }

    @AfterAll
    void closeBrowser() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    @BeforeEach
    void openFreshContext() {
        Browser.NewContextOptions options = new Browser.NewContextOptions()
                .setViewportSize(1440, 900);
        if (Config.recordVideo()) {
            options.setRecordVideoDir(VIDEO_RECORDING_DIR);
        }
        context = browser.newContext(options);
        context.setDefaultTimeout(10_000);
        context.tracing().start(new Tracing.StartOptions()
                .setScreenshots(true)
                .setSnapshots(true));
        page = context.newPage();
    }

    public Page page() {
        return page;
    }

    public BrowserContext context() {
        return context;
    }
}
