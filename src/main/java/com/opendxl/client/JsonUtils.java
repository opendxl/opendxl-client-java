/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opendxl.client.exception.DxlException;

/**
 * Json utility methods
 */
public class JsonUtils {

    /**
     * The Jackson object mapper instance
     */
    private static final ObjectMapper MAPPER =
        new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** Private constructor */
    protected JsonUtils() {
        super();
    }

    /**
     * Returns a JSON string representation of the specified object
     *
     * @param object The object to convert to a JSON string
     * @return JSON representation of the specified object
     * @throws DxlException If a DXL exception occurs
     */
    public static String toString(final Object object) throws DxlException {
        try {
            return MAPPER.writeValueAsString(object);
        } catch (Exception ex) {
            throw new DxlException("Error creating JSON string from object", ex);
        }
    }

    /**
     * Returns an object corresponding to the specified JSON string
     *
     * @param string The JSON string
     * @param clazz The object type
     * @param <T> The object type
     * @return An object corresponding to the specified JSON string
     * @throws DxlException If a DXL exception occurs
     */
    public static <T> T fromString(final String string, final Class<T> clazz) throws DxlException {
        try {
            return MAPPER.readValue(string, clazz);
        } catch (Exception ex) {
            throw new DxlException("Error creating objects from JSON string", ex);
        }
    }

    /**
     * Use if you need a collection of things to be converted from a json string like a List or Set
     *
     * @param string The JSON string
     * @param typeReference TypeReference
     * @param <T> The object type
     * @return An Collection of objects corresponding to the specified JSON string
     * @throws DxlException If a DXL exception occurs
     */
    public static <T> T fromString(final String string, TypeReference<T> typeReference) throws DxlException {
        try {
            ObjectMapper jsonMapper = new ObjectMapper();
            return jsonMapper.readValue(string, typeReference);
        } catch (Exception ex) {
            throw new DxlException("Error creating objects for JSON string", ex);
        }
    }
}
