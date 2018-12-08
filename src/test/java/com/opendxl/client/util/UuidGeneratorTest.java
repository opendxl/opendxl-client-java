/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client.util;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;

/**
 * Test cases for the {@link UuidGenerator} class.
 */
public class UuidGeneratorTest {
    /**
     * Generates a large number of random UUIDs and ensures that they can be converted
     * to/from trimmed strings (22 characters in length).
     */
    @Test
    public void testUuidGeneration() {
        for (int i = 0; i < 10000; i++) {
            UUID uuid = UUID.randomUUID();
            String orig = uuid.toString();
            String trimmed = UuidGenerator.toString(uuid);
            uuid = UuidGenerator.fromString(trimmed);
            assertEquals(orig, uuid.toString());
        }
    }
}
