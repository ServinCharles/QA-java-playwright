package com.example.data;

/** Petstore scenario data, loaded from testdata/petstore/pets.json. */
public record PetstoreData(
        String petName,
        String petStatus,
        long unknownPetId,
        String invalidPetId,
        String notFoundType,
        String notFoundMessage) {
}
