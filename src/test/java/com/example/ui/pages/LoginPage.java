package com.example.ui.pages;

import com.example.ui.base.BasePage;
import com.example.ui.locators.LoginLocators;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/** Page object for the OrangeHRM login screen. Actions only, no selectors. */
public class LoginPage extends BasePage {

    public LoginPage(Page page) {
        super(page);
    }

    public LoginPage open(String baseUrl) {
        navigate(baseUrl + "/auth/login");
        waitForVisible(LoginLocators.USERNAME);
        return this;
    }

    public LoginPage enterCredentials(String username, String password) {
        fill(LoginLocators.USERNAME, username);
        fill(LoginLocators.PASSWORD, password);
        return this;
    }

    /**
     * Clicks Login without waiting for the page it leads to to finish loading. The next page
     * is checked by the test itself (waitForUrl), so a slow resource cannot time out the click.
     */
    public void submit() {
        locator(LoginLocators.SUBMIT).click(new Locator.ClickOptions().setNoWaitAfter(true));
    }

    /** The banner shown when the server rejects the credentials. */
    public Locator errorMessage() {
        return locator(LoginLocators.ERROR_MESSAGE);
    }

    /** All validation messages shown under the fields, in page order. */
    public Locator fieldErrors() {
        return locator(LoginLocators.FIELD_ERROR);
    }
}
