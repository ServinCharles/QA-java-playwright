package com.example.support;

import java.util.List;
import java.util.Locale;

/**
 * Central place for environment-driven configuration.
 * Resolution order: JVM system property -> environment variable -> default.
 * Example: mvn test -DORANGEHRM_PASSWORD=secret   or   ORANGEHRM_PASSWORD=secret mvn test
 */
public final class Config {

    private static final List<String> BROWSERS = List.of("chromium", "firefox", "webkit");
    private static final List<String> VIDEO_MODES = List.of("on-failure", "off");

    private Config() {
    }

    public static String get(String key, String defaultValue) {
        String fromSystem = System.getProperty(key);
        if (fromSystem != null && !fromSystem.isBlank()) {
            return fromSystem;
        }
        String fromEnv = System.getenv(key);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        return defaultValue;
    }

    /** Browser engine for the UI tests: chromium (default), firefox or webkit. */
    public static String browser() {
        String value = get("BROWSER", "chromium").trim().toLowerCase(Locale.ROOT);
        if (!BROWSERS.contains(value)) {
            throw new IllegalArgumentException(
                    "Unsupported BROWSER '" + value + "'. Use one of: " + String.join(", ", BROWSERS));
        }
        return value;
    }

    public static boolean headless() {
        return Boolean.parseBoolean(get("HEADLESS", "true"));
    }

    /**
     * Whether UI tests are recorded. VIDEO=on-failure (default) records every UI test and keeps
     * the video only when the test fails. VIDEO=off records nothing.
     */
    public static boolean recordVideo() {
        String value = get("VIDEO", "on-failure").trim().toLowerCase(Locale.ROOT);
        if (!VIDEO_MODES.contains(value)) {
            throw new IllegalArgumentException(
                    "Unsupported VIDEO '" + value + "'. Use one of: " + String.join(", ", VIDEO_MODES));
        }
        return value.equals("on-failure");
    }

    /** Delay in milliseconds added to every browser action, for watching a headed run. 0 means off. */
    public static double slowMoMillis() {
        String value = get("SLOW_MO", "0").trim();
        try {
            double millis = Double.parseDouble(value);
            if (millis < 0) {
                throw new NumberFormatException();
            }
            return millis;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Unsupported SLOW_MO '" + value + "'. Use a number of milliseconds, such as 500.");
        }
    }

    public static String petstoreBaseUrl() {
        return get("PETSTORE_BASE_URL", "https://petstore.swagger.io/v2");
    }

    public static String orangeHrmBaseUrl() {
        return get("ORANGEHRM_BASE_URL", "https://opensource-demo.orangehrmlive.com/web/index.php");
    }

    /** Public OrangeHRM demo credentials. Override in CI for any non-demo environment. */
    public static String orangeHrmUser() {
        return get("ORANGEHRM_USERNAME", "Admin");
    }

    public static String orangeHrmPassword() {
        return get("ORANGEHRM_PASSWORD", "admin123");
    }
}
