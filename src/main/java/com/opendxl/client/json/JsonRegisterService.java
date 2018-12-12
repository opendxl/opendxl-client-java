/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.opendxl.client.DxlClient;
import com.opendxl.client.ServiceRegistrationInfo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Payload for a request to register a service with the Data Exchange Layer (DXL) fabric.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JsonRegisterService extends AbstractJsonMessage {
    /**
     * The service type
     */
    private final String serviceType;
    /**
     * The service instance ID
     */
    private final String instanceId;
    /**
     * The set of registered channels
     */
    private final Set<String> requestChannels;
    /**
     * The map of meta data associated with this service (name-value pairs)
     */
    private final Map<String, String> metadata;
    /**
     * The Time-To-Live (TTL) of the service registration (in minutes)
     */
    private final long ttlMins;

    /**
     * Constructs the JsonRegisterService object
     *
     * @param service The service registration info
     * @param client  The related DXL client
     */
    public JsonRegisterService(final DxlClient client, final ServiceRegistrationInfo service) {
        if (service == null) {
            throw new IllegalArgumentException("Undefined service object");
        }

        this.serviceType = service.getServiceType();
        this.instanceId = service.getServiceId();
        this.requestChannels = (service.getCallbacksByTopic().isEmpty() ? null
            : new HashSet<>(service.getCallbacksByTopic().keySet()));
        this.metadata = (service.getMetadata() == null || service.getMetadata().isEmpty() ? null
            : new HashMap<>(service.getMetadata()));
        this.ttlMins = service.getTtlMins();
    }

    /**
     * Constructs the JsonRegisterService object
     *
     * @param serviceType The service type
     * @param instanceId The service instance identifier
     * @param requestChannels The request channel names
     * @param metaData The meta data
     * @param ttlMins The ttl in minutes
     * @param client  The related DXL client
     */
    public JsonRegisterService(final DxlClient client, final String serviceType, final String instanceId,
                               final Set<String> requestChannels, final Map<String, String> metaData,
                               final long ttlMins) {
        this.serviceType = serviceType;
        this.instanceId = instanceId;
        this.requestChannels = requestChannels;
        this.metadata = metaData;
        this.ttlMins = ttlMins;
    }

    /**
     * Returns the service type
     *
     * @return The service type
     */
    @JsonProperty("serviceType")
    public String getServiceType() {
        return serviceType;
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

    /**
     * Returns the list of full qualified registered channels
     *
     * @return The list of full qualified registered channels
     */
    @JsonProperty("requestChannels")
    public Set<String> getRequestChannels() {
        return requestChannels;
    }

    /**
     * Returns the map of meta data associated with this service
     *
     * @return The map of meta data associated with this service
     */
    @JsonProperty("metaData")
    public Map<String, String> getMetadata() {
        return metadata;
    }

    /**
     * Returns the Time-To-Live (TTL) of the service registration (in minutes)
     *
     * @return The Time-To-Live (TTL) of the service registration (in minutes)
     */
    @JsonProperty("ttlMins")
    public long getTtlMins() {
        return ttlMins;
    }
}
