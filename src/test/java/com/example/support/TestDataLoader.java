package com.example.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Loads test data from JSON files under src/test/resources/testdata.
 * Keeping data out of test code means cases can be added without touching Java.
 */
public final class TestDataLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TestDataLoader() {
    }

    public static <T> T read(String resourcePath, Class<T> type) {
        try (InputStream in = open(resourcePath)) {
            return MAPPER.readValue(in, type);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read test data " + resourcePath, e);
        }
    }

    public static <T> T read(String resourcePath, TypeReference<T> type) {
        try (InputStream in = open(resourcePath)) {
            return MAPPER.readValue(in, type);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read test data " + resourcePath, e);
        }
    }

    private static InputStream open(String resourcePath) {
        InputStream in = TestDataLoader.class.getClassLoader().getResourceAsStream(resourcePath);
        if (in == null) {
            throw new IllegalStateException("Missing test data file on classpath: " + resourcePath);
        }
        return in;
    }
}
