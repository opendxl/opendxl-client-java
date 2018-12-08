/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client.json;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

/**
 * Abstract Json message for the Data Exchange Layer (DXL) fabric.
 */
public abstract class AbstractJsonMessage {
    /**
     * The Jackson object mapper instance
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Returns the Json representation of the object
     *
     * @return The Json representation of the object
     * @throws JsonProcessingException If a JSON error occurs
     */
    @JsonIgnore
    public String toJsonString()
        throws JsonProcessingException {
        return MAPPER.writeValueAsString(this);
    }

    /**
     * Returns the pretty Json representation of the object
     *
     * @return The pretty Json representation of the object
     * @throws JsonProcessingException If a JSON error occurs
     */
    @JsonIgnore
    public String toPrettyJsonString()
        throws JsonProcessingException {
        ObjectWriter writer = MAPPER.writer().withDefaultPrettyPrinter();
        return writer.writeValueAsString(this);
    }
}
