package com.example.data;

import java.util.List;

/**
 * One data-driven login scenario, loaded from testdata/orangehrm/login.json.
 * When useConfigCredentials is true, the username and password come from Config
 * (environment variables) instead of the file, so no secrets live in the repo.
 *
 * A failing scenario sets one of these. Each value is a key into copy.json (see CopyDeck),
 * so the approved wording lives in one file:
 *   expectedErrorKey        the banner message shown after the server rejects the login
 *   expectedFieldErrorKeys  the messages shown under the fields by client-side validation, in page order
 */
public record LoginCase(
        String name,
        String username,
        String password,
        boolean useConfigCredentials,
        boolean expectSuccess,
        String expectedErrorKey,
        List<String> expectedFieldErrorKeys) {

    /** Shows the scenario name in the test report instead of the record dump. */
    @Override
    public String toString() {
        return name;
    }
}
