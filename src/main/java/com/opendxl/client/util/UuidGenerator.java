/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client.util;

import java.util.UUID;

/**
 * Generator used to generate a universally unique identifier (UUID) string that is all lowercase and has enclosing
 * brackets
 */
public class UuidGenerator {

    /** Constructor */
    protected UuidGenerator() {
        super();
    }

    /**
     * Generates and returns a UUID
     *
     * @return The generated UUID
     */
    private static UUID generateId() {
        return UUID.randomUUID();
    }

    /**
     * Generates and returns a random UUID that is all lowercase and has enclosing brackets
     *
     * @return A UUID string that is all lowercase and has enclosing brackets
     */
    public static String generateIdAsString() {
        return toString(generateId());
    }

    /**
     * Converts the specified UUID into string that is all lowercase and has enclosing brackets
     *
     * @param uuid The UUID
     * @return A UUID string that is all lowercase and has enclosing brackets
     */
    public static String toString(final UUID uuid) {
        return "{" + uuid.toString().toLowerCase() + "}";
    }

    /**
     * Converts the specified UUID string into a UUID instance
     *
     * @param uuid The UUID string
     * @return The corresponding UUID instance
     */
    protected static UUID fromString(final String uuid) {
        return UUID.fromString(uuid.replaceAll("[{}]+", ""));
    }

    /**
     * Normalizes the specified UUID string
     *
     * @param uuid The UUID string
     * @return A UUID string that is all lowercase and has enclosing brackets
     */
    public static String normalize(final String uuid) {
        return toString(fromString(uuid));
    }
}
