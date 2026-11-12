package com.example.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.RequestOptions;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Thin, endpoint-focused client for the Swagger Petstore {@code /pet} resource.
 * It only knows HTTP and payload shapes. It does not assert anything, so tests stay readable.
 */
public class PetStoreClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final APIRequestContext request;
    private final String baseUrl;

    public PetStoreClient(APIRequestContext request, String baseUrl) {
        this.request = request;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** POST /pet */
    public APIResponse createPet(JsonNode pet) {
        return request.post(baseUrl + "/pet", RequestOptions.create()
                .setHeader("Content-Type", "application/json")
                .setHeader("Accept", "application/json")
                .setData(pet.toString()));
    }

    /** GET /pet/{petId} with a raw string, so invalid input can be sent deliberately. */
    public APIResponse getPet(String petId) {
        return request.get(baseUrl + "/pet/" + petId, RequestOptions.create()
                .setHeader("Accept", "application/json"));
    }

    public APIResponse getPet(long petId) {
        return getPet(String.valueOf(petId));
    }

    /** DELETE /pet/{petId}. Used for cleanup and to guarantee a pet is absent. */
    public APIResponse deletePet(long petId) {
        return request.delete(baseUrl + "/pet/" + petId, RequestOptions.create()
                .setHeader("Accept", "application/json"));
    }

    /** Builds a valid Pet payload. */
    public static JsonNode newPet(long id, String name, String status) {
        ObjectNode pet = MAPPER.createObjectNode();
        pet.put("id", id);

        ObjectNode category = pet.putObject("category");
        category.put("id", 1);
        category.put("name", "dogs");

        pet.put("name", name);
        pet.putArray("photoUrls").add("https://example.com/photo.jpg");

        ObjectNode tag = pet.putArray("tags").addObject();
        tag.put("id", 1);
        tag.put("name", "qa-automation");

        pet.put("status", status);
        return pet;
    }

    public static JsonNode parse(APIResponse response) throws IOException {
        return MAPPER.readTree(response.text());
    }

    /** The public Petstore is shared, so IDs are randomised to avoid collisions between runs. */
    public static long randomPetId() {
        return ThreadLocalRandom.current().nextLong(100_000_000L, 999_999_999L);
    }
}
