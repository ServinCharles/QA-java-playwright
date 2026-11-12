package com.example.support;

/**
 * Central place for environment-driven configuration.
 * Resolution order: JVM system property -> environment variable -> default.
 * Example: mvn test -DORANGEHRM_PASSWORD=secret   or   ORANGEHRM_PASSWORD=secret mvn test
 */
public final class Config {

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

    public static boolean headless() {
        return Boolean.parseBoolean(get("HEADLESS", "true"));
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
