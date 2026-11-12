package com.example.ui.base;

import com.example.support.Config;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Shared UI test lifecycle: one browser for the class, one isolated context per test.
 * Tracing and failure screenshots are handled by UiArtifactsExtension.
 */
@Tag("ui")
@ExtendWith(UiArtifactsExtension.class)
public abstract class BaseUiTest {

    private static Playwright playwright;
    private static Browser browser;

    private BrowserContext context;
    private Page page;

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(Config.headless()));
    }

    @AfterAll
    static void closeBrowser() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    @BeforeEach
    void openFreshContext() {
        context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 900));
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
