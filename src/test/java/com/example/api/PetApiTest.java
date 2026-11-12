package com.example.api;

import com.example.data.PetstoreData;
import com.example.support.Config;
import com.example.support.SchemaValidator;
import com.example.support.TestDataLoader;
import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * API tests for the Swagger Petstore /pet endpoint.
 * Each test checks three things: the status code, the response contract (JSON Schema),
 * and the values or error message in the body. That covers create, read and delete.
 * Data lives in testdata/petstore/pets.json. Schemas live in src/test/resources/schemas.
 */
@Tag("api")
@DisplayName("Swagger Petstore: /pet")
class PetApiTest {

    private static final String PET_SCHEMA = "schemas/pet.schema.json";
    private static final String API_RESPONSE_SCHEMA = "schemas/api-response.schema.json";

    private static Playwright playwright;
    private static APIRequestContext requestContext;
    private static PetStoreClient pets;
    private static PetstoreData data;

    @BeforeAll
    static void setUp() {
        data = TestDataLoader.read("testdata/petstore/pets.json", PetstoreData.class);
        playwright = Playwright.create();
        requestContext = playwright.request().newContext(new APIRequest.NewContextOptions()
                .setExtraHTTPHeaders(Map.of("Accept", "application/json"))
                .setTimeout(15_000));
        pets = new PetStoreClient(requestContext, Config.petstoreBaseUrl());
    }

    @AfterAll
    static void tearDown() {
        if (requestContext != null) {
            requestContext.dispose();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    @Test
    @DisplayName("POST a pet, then GET it by id returns the same data and matches the Pet contract (positive)")
    void createdPetCanBeRetrieved() throws IOException {
        long id = PetStoreClient.randomPetId();
        String name = data.petName() + "-" + id;
        try {
            APIResponse created = pets.createPet(PetStoreClient.newPet(id, name, data.petStatus()));
            assertEquals(200, created.status(), () -> "create failed: " + created.text());
            assertJsonContentType(created);
            SchemaValidator.assertMatches(PET_SCHEMA, PetStoreClient.parse(created));

            APIResponse fetched = getPetWhenAvailable(id);
            assertEquals(200, fetched.status(), () -> "fetch failed: " + fetched.text());
            assertJsonContentType(fetched);

            JsonNode pet = PetStoreClient.parse(fetched);
            SchemaValidator.assertMatches(PET_SCHEMA, pet);
            assertAll("pet read back matches what was created",
                    () -> assertEquals(id, pet.get("id").asLong()),
                    () -> assertEquals(name, pet.get("name").asText()),
                    () -> assertEquals(data.petStatus(), pet.get("status").asText()));
        } finally {
            pets.deletePet(id);
        }
    }

    @Test
    @DisplayName("GET an unknown pet id returns 404 with the 'Pet not found' error (negative)")
    void unknownPetReturnsNotFound() throws IOException {
        // The server is shared, so make sure nobody has created this id.
        pets.deletePet(data.unknownPetId());

        APIResponse response = pets.getPet(data.unknownPetId());
        assertEquals(404, response.status());
        assertJsonContentType(response);

        JsonNode error = PetStoreClient.parse(response);
        SchemaValidator.assertMatches(API_RESPONSE_SCHEMA, error);
        assertAll("not-found error body",
                () -> assertEquals(data.notFoundType(), error.get("type").asText()),
                () -> assertEquals(data.notFoundMessage(), error.get("message").asText()));
    }

    @Test
    @DisplayName("GET with a non-numeric pet id is rejected with a 4xx error body (negative)")
    void invalidPetIdIsRejected() throws IOException {
        APIResponse response = pets.getPet(data.invalidPetId());
        assertTrue(response.status() >= 400 && response.status() < 500,
                () -> "expected a 4xx response but got " + response.status());
        assertJsonContentType(response);

        // The message text here is an internal exception string, so only its contract is asserted.
        SchemaValidator.assertMatches(API_RESPONSE_SCHEMA, PetStoreClient.parse(response));
    }

    @Test
    @DisplayName("DELETE a pet returns 200 with a confirmation, and the pet can no longer be read (positive)")
    void deletedPetIsNoLongerFound() throws IOException {
        long id = PetStoreClient.randomPetId();
        APIResponse created = pets.createPet(
                PetStoreClient.newPet(id, data.petName() + "-" + id, data.petStatus()));
        assertEquals(200, created.status(), () -> "create failed: " + created.text());

        APIResponse deleted = deletePetWhenAvailable(id);
        assertEquals(200, deleted.status(), () -> "delete failed: " + deleted.text());
        assertJsonContentType(deleted);

        JsonNode confirmation = PetStoreClient.parse(deleted);
        SchemaValidator.assertMatches(API_RESPONSE_SCHEMA, confirmation);
        assertAll("delete confirmation body",
                () -> assertEquals(200, confirmation.get("code").asInt()),
                () -> assertEquals(String.valueOf(id), confirmation.get("message").asText()));

        APIResponse afterDelete = getPetUntilGone(id);
        assertEquals(404, afterDelete.status(), "the deleted pet should no longer be found");
        assertEquals(data.notFoundMessage(), PetStoreClient.parse(afterDelete).get("message").asText());
    }

    @Test
    @DisplayName("DELETE an unknown pet id returns 404 (negative)")
    void deletingUnknownPetReturnsNotFound() {
        // The server is shared, so make sure nobody has created this id.
        pets.deletePet(data.unknownPetId());

        APIResponse response = pets.deletePet(data.unknownPetId());
        assertEquals(404, response.status());
    }

    // ---- helpers ------------------------------------------------------------------

    private static void assertJsonContentType(APIResponse response) {
        String contentType = response.headers().getOrDefault("content-type", "");
        assertTrue(contentType.contains("application/json"),
                () -> "expected a JSON content type but got '" + contentType + "'");
    }

    /**
     * The public Petstore runs on several instances, so a read straight after a write
     * can briefly return 404. Poll for a short time before treating it as a failure.
     */
    private static APIResponse getPetWhenAvailable(long id) {
        APIResponse response = pets.getPet(id);
        for (int attempt = 1; attempt < 6 && response.status() == 404; attempt++) {
            pause();
            response = pets.getPet(id);
        }
        return response;
    }

    /** Same reason as above: a delete straight after a create can briefly return 404. */
    private static APIResponse deletePetWhenAvailable(long id) {
        APIResponse response = pets.deletePet(id);
        for (int attempt = 1; attempt < 6 && response.status() == 404; attempt++) {
            pause();
            response = pets.deletePet(id);
        }
        return response;
    }

    /** And the reverse: a read straight after a delete can briefly still return the pet. */
    private static APIResponse getPetUntilGone(long id) {
        APIResponse response = pets.getPet(id);
        for (int attempt = 1; attempt < 6 && response.status() == 200; attempt++) {
            pause();
            response = pets.getPet(id);
        }
        return response;
    }

    private static void pause() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
