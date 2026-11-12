package com.example.support;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the file name used for a test's failure evidence (screenshot, trace, video).
 * The extension that saves the files and the report that links to them both call this,
 * so the names always match.
 *
 * Each case of a data-driven test gets its own name, for example LoginTest.loginBehavesAsExpected-2,
 * so cases do not overwrite each other.
 */
public final class ArtifactNames {

    private static final Pattern INVOCATION = Pattern.compile("\\[test-template-invocation:#(\\d+)]");

    private ArtifactNames() {
    }

    public static String forTest(String className, String methodName, String uniqueId) {
        String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
        StringBuilder name = new StringBuilder(simpleClassName).append('.').append(methodName);
        Matcher invocation = INVOCATION.matcher(uniqueId == null ? "" : uniqueId);
        if (invocation.find()) {
            name.append('-').append(invocation.group(1));
        }
        return name.toString().replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
