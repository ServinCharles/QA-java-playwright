package com.example.support;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;
import java.util.Map;

/**
 * The approved copy (user-facing wording) for OrangeHRM, loaded from testdata/orangehrm/copy.json.
 *
 * Tests and test data refer to messages by key, for example "login.invalidCredentials", so
 * each message is written once. When the wording changes, only copy.json changes.
 */
public final class CopyDeck {

    private static final String COPY_FILE = "testdata/orangehrm/copy.json";
    private static final Map<String, String> COPY =
            TestDataLoader.read(COPY_FILE, new TypeReference<Map<String, String>>() {
            });

    private CopyDeck() {
    }

    /** The approved text for a key. Fails fast on an unknown key, so a typo cannot pass silently. */
    public static String text(String key) {
        String text = COPY.get(key);
        if (text == null) {
            throw new IllegalArgumentException("No copy for key '" + key + "' in " + COPY_FILE);
        }
        return text;
    }

    /** The approved text for each key, in the same order. */
    public static List<String> texts(List<String> keys) {
        return keys.stream().map(CopyDeck::text).toList();
    }
}
