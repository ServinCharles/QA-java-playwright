package com.example.ui.pages;

import com.example.ui.base.BasePage;
import com.example.ui.locators.DashboardLocators;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/** Page object for the OrangeHRM dashboard, shown after a successful login. */
public class DashboardPage extends BasePage {

    public DashboardPage(Page page) {
        super(page);
    }

    public Locator breadcrumb() {
        return locator(DashboardLocators.BREADCRUMB);
    }
}
