/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.opendxl.client.ServiceRegistrationInfo;

/**
 * Payload for a request to unregister a service from the Data Exchange Layer (DXL) fabric.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JsonUnregisterService extends AbstractJsonMessage {
    /**
     * The service instance ID
     */
    private final String instanceId;

    /**
     * Constructs the JsonUnregisterService object
     *
     * @param service The service registration info
     */
    public JsonUnregisterService(final ServiceRegistrationInfo service) {
        if (service == null) {
            throw new IllegalArgumentException("Undefined service object");
        }

        this.instanceId = service.getServiceGuid();
    }

    /**
     * Constructs the JsonUnregisterService object
     *
     * @param instanceId The instance ID of the service
     */
    public JsonUnregisterService(final String instanceId) {
        if (instanceId == null || instanceId.isEmpty()) {
            throw new IllegalArgumentException("Undefined service identifier");
        }

        this.instanceId = instanceId;
    }

    /**
     * Returns the instance ID of the service
     *
     * @return The instance ID of the service
     */
    @JsonProperty("serviceGuid")
    public String getInstanceId() {
        return instanceId;
    }
}
