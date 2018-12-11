/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client;

import com.opendxl.client.callback.RequestCallback;
import com.opendxl.client.exception.DxlException;
import com.opendxl.client.util.UuidGenerator;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Service registration information for the Data Exchange Layer (DXL) fabric.
 */
public class ServiceRegistrationInfo {
    /**
     * The service type or name prefix
     */
    private final String serviceType;
    /**
     * The unique service ID
     */
    private final String serviceGuid;
    /**
     * The map of registered topics and their associated callbacks
     */
    private final Map<String, Set<RequestCallback>> callbacksByTopic = new HashMap<>();
    /**
     * The map of meta data associated with this service (name-value pairs)
     */
    private Map<String, String> metadata = null;
    /**
     * The Time-To-Live (TTL) of the service registration (in minutes)
     */
    private long ttlMins;
    /**
     * The Time-To-Live (TTL) of the service registration (default: 60 minutes)
     */
    private long ttl =
        Long.parseLong(System.getProperty(Constants.SYSPROP_SERVICE_TTL_DEFAULT, Long.toString(60)));
    /**
     * The minimum Time-To-Live (TTL) of the service registration (default: 10 minutes)
     */
    private long ttlLowerLimit =
        Long.parseLong(System.getProperty(Constants.SYSPROP_SERVICE_TTL_LOWER_LIMIT, Long.toString(10)));
    /**
     * The Time-To-Live (TTL) resolution factor (FOR TESTING ONLY)
     */
    private long ttlResolution =
        System.getProperty(Constants.SYSPROP_SERVICE_TTL_RESOLUTION, "min").equalsIgnoreCase("sec") ? 60L : 1L;

    /**
     * The internal client reference
     */
    private final DxlClient client;

    /**
     * Registration sync object
     */
    private final Object registrationSync = new Object();
    /**
     * Whether at least one registration has occurred
     */
    private boolean registationOccurred = false;
    /**
     * Whether at least one unregistration registration has occurred
     */
    private boolean unregistrationOccurred = false;
    /**
     * The set of tenants that the service will be available to
     */
    @SuppressWarnings("unchecked")
    private Set<String> destTenantGuids = Collections.EMPTY_SET;

    /**
     * Constructs the ServiceRegistrationInfo object
     *
     * @param client      {@link DxlClient}
     * @param serviceType The service type or name prefix
     */
    public ServiceRegistrationInfo(final DxlClient client, final String serviceType) {
        if (serviceType == null || serviceType.isEmpty()) {
            throw new IllegalArgumentException("Undefined service name");
        }

        this.serviceType = serviceType;
        this.serviceGuid = UuidGenerator.generateIdAsString();
        this.ttlMins = Math.max(1, this.ttl / this.ttlResolution);
        this.client = client;
    }

    /**
     * Returns the service type
     *
     * @return The service type
     */
    public String getServiceType() {
        return serviceType;
    }

    /**
     * Returns the instance ID of the service
     *
     * @return The instance ID of the service
     */
    public String getServiceGuid() {
        return serviceGuid;
    }

    /**
     * Returns the Time-To-Live (TTL) of the service registration (in minutes)
     *
     * @return The Time-To-Live (TTL) of the service registration (in minutes)
     */
    public long getTtlMins() {
        return ttlMins;
    }

    /**
     * Returns the Time-To-Live (TTL) of the service registration
     *
     * @return The Time-To-Live (TTL) of the service registration
     */
    public long getTtl() {
        return ttl;
    }

    /**
     * Sets the Time-To-Live (TTL) of the service registration
     *
     * @param ttl The Time-To-Live (TTL) of the service registration
     */
    public void setTtl(final long ttl)
        throws IllegalArgumentException {
        if (ttl < ttlLowerLimit) {
            throw new IllegalArgumentException("Invalid TTL value (must be " + ttlLowerLimit + " or higher)");
        }
        this.ttl = ttl;
        this.ttlMins = Math.max(1, this.ttl / this.ttlResolution);
    }

    /**
     * Returns the Time-To-Live (TTL) resolution factor
     *
     * @return The Time-To-Live (TTL) resolution factor
     */
    public long getTtlResolution() {
        return ttlResolution;
    }

    /**
     * Returns the list of full qualified registered topics
     *
     * @return The list of full qualified registered topics
     */
    public Set<String> getTopics() {
        return new HashSet<>(callbacksByTopic.keySet());
    }

    /**
     * Returns the map of registered topics and their associated callbacks
     *
     * @return The map of registered topics and their associated callbacks
     */
    public Map<String, Set<RequestCallback>> getCallbacksByTopic() {
        return callbacksByTopic;
    }

    /**
     * Adds one or more request topics and an associated callback to the service.
     *
     * @param topicAndCallback The map of request topics and their associated callbacks
     * @throws DxlException If a DXL exception occurs
     */
    public void setCallbacksByTopic(Map<String, RequestCallback> topicAndCallback)
        throws DxlException {
        if (topicAndCallback == null || topicAndCallback.isEmpty()) {
            throw new IllegalArgumentException("Undefined topic");
        }

        for (Map.Entry<String, RequestCallback> entry : topicAndCallback.entrySet()) {
            addTopic(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Adds a request topic and associated callback to the service.
     *
     * @param topic  The request topic without service name prefix
     * @param callback The callback associated with this request topic
     */
    public void addTopic(final String topic, final RequestCallback callback) {
        if (topic == null || topic.isEmpty()) {
            throw new IllegalArgumentException("Undefined topic");
        }
        if (callback == null) {
            throw new IllegalArgumentException("Undefined callback");
        }

        Set<RequestCallback> callbacks = callbacksByTopic.get(topic);
        //noinspection Java8MapApi
        if (callbacks == null) {
            callbacks = new HashSet<>();
            callbacksByTopic.put(topic, callbacks);
        }
        callbacks.add(callback);
    }

    /**
     * Removes a request topic and associated callback to the service.
     *
     * @param topic  The request topic without service name prefix
     * @param callback The callback associated with this request topic
     */
    public void removeTopic(final String topic, final RequestCallback callback) {
        if (topic == null || topic.isEmpty()) {
            throw new IllegalArgumentException("Undefined topic");
        }
        if (callback == null) {
            throw new IllegalArgumentException("Undefined callback");
        }

        Set<RequestCallback> callbacks = callbacksByTopic.get(topic);
        if (callbacks != null) {
            callbacks.remove(callback);
            if (callbacks.size() == 0) {
                callbacksByTopic.remove(topic);
            }
        }
    }

    /**
     * Returns the map of meta data associated with this service
     *
     * @return The map of meta data associated with this service
     */
    public Map<String, String> getMetadata() {
        return metadata;
    }

    /**
     * Sets the map of meta data associated with this service
     *
     * @param metadata The map of meta data associated with this service
     */
    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    /**
     * Add a name/value pair to the meta data associated with this service
     *
     * @param name  Name of the meta data property
     * @param value Value of the meta data property
     */
    public void addMetadata(final String name, final String value) {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        metadata.put(name, value);
    }

    /**
     * Waits for a registration notification (register or unregister)
     *
     * @param waitTime   The amount of time to wait
     * @param isRegister Whether we are waiting for a register or unregister notification
     */
    private void waitForRegistrationNotification(final long waitTime, final boolean isRegister)
        throws DxlException {
        synchronized (registrationSync) {
            if (waitTime > 0) {
                try {
                    registrationSync.wait(waitTime);
                } catch (InterruptedException ex) { /**/ }
            } else {
                throw new DxlException(
                    "Timeout waiting for service "
                        + (isRegister ? "registration" : "unregistration")
                        + " to occur");
            }
        }
    }

    /**
     * Waits for the service to be registered with the broker for the first time
     *
     * @param timeout The amount of time to wait for the registration to occur.
     * @throws DxlException If a DXL exception occurs
     */
    public void waitForRegistration(final long timeout) throws DxlException {
        final long endTime = System.currentTimeMillis() + timeout;
        synchronized (registrationSync) {
            while (!registationOccurred) {
                waitForRegistrationNotification(
                    endTime - System.currentTimeMillis(), true);
            }
        }
    }

    /**
     * Invoked when the service has been successfully registered with a broker
     */
    public void notifyRegistrationSucceeded() {
        synchronized (registrationSync) {
            registationOccurred = true;
            unregistrationOccurred = false;
            registrationSync.notifyAll();
        }
    }

    /**
     * Waits for the service to be unregistered with the broker for the first time
     *
     * @param timeout The amount of time to wait for the unregistration to occur.
     * @throws DxlException If a DXL exception occurs
     */
    public void waitForUnregistration(final long timeout) throws DxlException {
        final long endTime = System.currentTimeMillis() + timeout;
        synchronized (registrationSync) {
            while (!unregistrationOccurred) {
                waitForRegistrationNotification(
                    endTime - System.currentTimeMillis(), false);
            }
        }
    }

    /**
     * Invoked when the service has been successfully unregistered with a broker
     */
    public void notifyUnregistrationSucceeded() {
        synchronized (registrationSync) {
            unregistrationOccurred = true;
            registationOccurred = false;
            registrationSync.notifyAll();
        }
    }

    /**
     * Invoked when the registration info is finalized
     */
    protected void finalize()
        throws Throwable {
        try {
            if (client != null) {
                client.unregisterServiceAsync(this);
            }
        } finally {
            super.finalize();
        }
    }

    /**
     * Returns the set of tenants that the service will be available to
     *
     * @return The set of tenants that the service will be available to
     */
    public Set<String> getDestTenantGuids() {
        return destTenantGuids;
    }

    /**
     * Sets the list of tenants that the service will be available to
     *
     * @param destTenantGuids The set of tenants that the service will be available to
     */
    public void setDestTenantGuids(final Set<String> destTenantGuids) {
        this.destTenantGuids = destTenantGuids;
    }
}
