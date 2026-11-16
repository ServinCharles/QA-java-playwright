package com.example.ui.tests;

import com.example.support.Config;
import com.example.ui.base.AccessibilityChecks;
import com.example.ui.base.BaseUiTest;
import com.example.ui.pages.LoginPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Accessibility checks with axe-core. Rules and blocking levels are in AccessibilityChecks. */
@Tag("accessibility")
@DisplayName("OrangeHRM accessibility")
class AccessibilityTest extends BaseUiTest {

    @Test
    @DisplayName("login page has no serious or critical WCAG A/AA violations")
    void loginPageHasNoBlockingViolations() {
        new LoginPage(page()).open(Config.orangeHrmBaseUrl());
        AccessibilityChecks.assertNoBlockingViolations(page());
    }
}
