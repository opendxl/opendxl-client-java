/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Payload for a request to unregister a service from the Data Exchange Layer (DXL) fabric.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
class JsonUnregisterService extends AbstractJsonMessage {

    /**
     * The service instance identifer
     */
    private final String instanceId;

    /**
     * Constructor for {@link JsonUnregisterService}
     *
     * @param service The service registration info
     */
    JsonUnregisterService(final ServiceRegistrationInfo service) {
        if (service == null) {
            throw new IllegalArgumentException("Undefined service object");
        }

        this.instanceId = service.getServiceId();
    }

    /**
     * Constructor for {@link JsonUnregisterService}
     *
     * @param instanceId The instance ID of the service
     */
    JsonUnregisterService(final String instanceId) {
        if (instanceId == null || instanceId.isEmpty()) {
            throw new IllegalArgumentException("Undefined service identifier");
        }

        this.instanceId = instanceId;
    }

    /**
     * Returns the instance identifier of the service
     *
     * @return The instance identifier of the service
     */
    @JsonProperty("serviceGuid")
    public String getInstanceId() {
        return instanceId;
    }
}
