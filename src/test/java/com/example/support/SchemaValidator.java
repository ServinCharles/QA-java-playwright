package com.example.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Contract checks: validates a JSON response body against a JSON Schema (draft-07)
 * stored under src/test/resources/schemas. A failure lists every violation at once.
 */
public final class SchemaValidator {

    private static final JsonSchemaFactory FACTORY =
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
    private static final Map<String, JsonSchema> CACHE = new ConcurrentHashMap<>();

    private SchemaValidator() {
    }

    public static void assertMatches(String schemaResource, JsonNode body) {
        JsonSchema schema = CACHE.computeIfAbsent(schemaResource, SchemaValidator::load);
        Set<ValidationMessage> violations = schema.validate(body);
        if (!violations.isEmpty()) {
            String details = violations.stream()
                    .map(ValidationMessage::getMessage)
                    .sorted()
                    .collect(Collectors.joining("\n  - ", "\n  - ", ""));
            throw new AssertionError("Response does not match the contract in " + schemaResource
                    + ":" + details + "\nBody: " + body);
        }
    }

    private static JsonSchema load(String schemaResource) {
        try (InputStream in = SchemaValidator.class.getClassLoader().getResourceAsStream(schemaResource)) {
            if (in == null) {
                throw new IllegalStateException("Missing schema file on classpath: " + schemaResource);
            }
            return FACTORY.getSchema(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read schema " + schemaResource, e);
        }
    }
}
