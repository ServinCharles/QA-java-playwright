package com.example.ui.tests;

import com.example.data.LoginCase;
import com.example.support.Config;
import com.example.support.CopyDeck;
import com.example.support.TestDataLoader;
import com.example.ui.base.BaseUiTest;
import com.example.ui.base.CopyText;
import com.example.ui.pages.DashboardPage;
import com.example.ui.pages.LoginPage;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/** Data-driven login tests. Scenarios live in testdata/orangehrm/login.json. */
@DisplayName("OrangeHRM login")
class LoginTest extends BaseUiTest {

    static Stream<LoginCase> loginCases() {
        return TestDataLoader.read("testdata/orangehrm/login.json",
                new TypeReference<List<LoginCase>>() {
                }).stream();
    }

    @ParameterizedTest
    @MethodSource("loginCases")
    void loginBehavesAsExpected(LoginCase data) {
        String username = data.useConfigCredentials() ? Config.orangeHrmUser() : data.username();
        String password = data.useConfigCredentials() ? Config.orangeHrmPassword() : data.password();

        LoginPage login = new LoginPage(page()).open(Config.orangeHrmBaseUrl());
        login.enterCredentials(username, password).submit();

        if (data.expectSuccess()) {
            assertThat(page()).hasURL(Pattern.compile(".*/dashboard/index.*"));
            assertThat(new DashboardPage(page()).breadcrumb()).hasText("Dashboard");
        } else {
            // Every rejected login must leave the user on the login page.
            assertThat(page()).hasURL(Pattern.compile(".*/auth/login.*"));

            if (data.expectedErrorKey() != null) {
                // Server-side rejection: a visible banner with the approved message from copy.json.
                String expectedError = CopyDeck.text(data.expectedErrorKey());
                assertThat(login.errorMessage()).isVisible();
                assertThat(login.errorMessage()).hasText(expectedError);
                // Copy check: the exact wording, case and punctuation users read.
                CopyText.assertExact(login.errorMessage(), expectedError);
            }
            if (data.expectedFieldErrorKeys() != null) {
                // Client-side validation: one message per invalid field, in order, and no banner.
                List<String> expectedFieldErrors = CopyDeck.texts(data.expectedFieldErrorKeys());
                assertThat(login.fieldErrors())
                        .hasText(expectedFieldErrors.toArray(new String[0]));
                CopyText.assertExact(login.fieldErrors(), expectedFieldErrors);
                assertThat(login.errorMessage()).isHidden();
            }
        }
    }
}
