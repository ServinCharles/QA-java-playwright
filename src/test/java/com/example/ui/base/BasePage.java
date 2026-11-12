package com.example.ui.base;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.util.regex.Pattern;

/**
 * Base class for all page objects. It holds the reusable interactions (click, fill,
 * dropdown, radio, checkbox, buttons such as Continue or Save, waits, and reads).
 * Page classes call these with selectors from their locator class, so no page
 * contains raw Playwright calls or hard-coded selectors.
 */
public abstract class BasePage {

    protected final Page page;

    protected BasePage(Page page) {
        this.page = page;
    }

    public Page page() {
        return page;
    }

    // ---- navigation and waits -------------------------------------------------

    protected void navigate(String url) {
        page.navigate(url);
    }

    protected void waitForVisible(String selector) {
        locator(selector).waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
    }

    protected void waitForUrl(Pattern urlPattern) {
        page.waitForURL(urlPattern);
    }

    // ---- element lookup -------------------------------------------------------

    protected Locator locator(String selector) {
        return page.locator(selector);
    }

    // ---- common interactions ----------------------------------------------------

    protected void click(String selector) {
        locator(selector).click();
    }

    /** Clicks a button by its accessible name, e.g. clickButton("Continue"). */
    protected void clickButton(String name) {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name)).click();
    }

    protected void clickContinue() {
        clickButton("Continue");
    }

    protected void clickSave() {
        clickButton("Save");
    }

    protected void fill(String selector, String value) {
        locator(selector).fill(value);
    }

    protected void clear(String selector) {
        locator(selector).fill("");
    }

    /** Selects an option in a native select by its visible label. */
    protected void selectOption(String selector, String visibleLabel) {
        locator(selector).selectOption(visibleLabel);
    }

    protected void check(String selector) {
        locator(selector).check();
    }

    protected void uncheck(String selector) {
        locator(selector).uncheck();
    }

    /** Ticks a radio button by its accessible label, e.g. checkRadio("Male"). */
    protected void checkRadio(String label) {
        page.getByRole(AriaRole.RADIO, new Page.GetByRoleOptions().setName(label)).check();
    }

    // ---- reads ------------------------------------------------------------------

    protected String text(String selector) {
        return locator(selector).innerText();
    }

    protected boolean isVisible(String selector) {
        return locator(selector).isVisible();
    }
}
