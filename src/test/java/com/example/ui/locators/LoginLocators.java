package com.example.ui.locators;

/**
 * All selectors for the OrangeHRM login page live here, not in the page object.
 * When the app changes, only this file changes.
 * Prefer stable attributes (name, type, role) over generated CSS classes.
 */
public final class LoginLocators {

    public static final String USERNAME = "input[name='username']";
    public static final String PASSWORD = "input[name='password']";
    public static final String SUBMIT = "button[type='submit']";
    /** Banner shown when the server rejects the credentials. */
    public static final String ERROR_MESSAGE = ".oxd-alert-content-text";
    /** Message shown under a field by client-side validation, e.g. "Required". */
    public static final String FIELD_ERROR = ".oxd-input-field-error-message";

    private LoginLocators() {
    }
}
