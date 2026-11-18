package com.example.ui.base;

import com.microsoft.playwright.Locator;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Copy text checks: the exact wording users read, character for character.
 *
 * Playwright's hasText() is a web-first check that waits for the text, but it normalises
 * whitespace before comparing. These checks run after it and compare the rendered text
 * (innerText) exactly: wording, case, punctuation and spacing. They also fail on leading or
 * trailing whitespace, so a copy change or a formatting slip is caught.
 *
 * The approved copy lives in testdata/orangehrm/copy.json and is read through CopyDeck.
 */
public final class CopyText {

    private CopyText() {
    }

    /** Asserts one element shows exactly the expected copy. */
    public static void assertExact(Locator element, String expected) {
        String actual = element.innerText();
        assertAll("copy text",
                () -> assertEquals(expected, actual, "copy text does not match the approved wording"),
                () -> assertNoStrayWhitespace(actual));
    }

    /** Asserts a list of elements shows exactly the expected copy, in page order. */
    public static void assertExact(Locator elements, List<String> expected) {
        List<String> actual = elements.allInnerTexts();
        assertEquals(expected, actual, "copy text does not match the approved wording, in order");
        for (String text : actual) {
            assertNoStrayWhitespace(text);
        }
    }

    private static void assertNoStrayWhitespace(String text) {
        assertEquals(text.strip(), text,
                () -> "copy text has leading or trailing whitespace: '" + text + "'");
    }
}
