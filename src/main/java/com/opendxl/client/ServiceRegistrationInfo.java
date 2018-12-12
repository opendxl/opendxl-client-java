/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client;

import com.opendxl.client.callback.RequestCallback;
import com.opendxl.client.exception.DxlException;
import com.opendxl.client.message.Request;
import com.opendxl.client.util.UuidGenerator;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Service Registration instances are used to register and expose services onto a DXL fabric.
 * <P>
 * DXL Services are exposed to the DXL fabric and are invoked in a fashion similar to RESTful web services.
 * Communication between an invoking client and the DXL service is one-to-one (request/response).
 * </P>
 * <P>
 * Each service is identified by the "topics" it responds to. Each of these "topics" can be thought of as a method that
 * is being "invoked" on the service by the remote client.
 * </P>
 * <P>
 * Multiple service "instances" can be registered with the DXL fabric that respond to the same "topics". When this
 * occurs (unless explicitly overridden by the client) the fabric will select the particular instance to route the
 * request to (by default round-robin). Multiple service instances can be used to increase scalability and
 * fault-tolerance.
 * </P>
 * <P>
 * The following demonstrates registering a service that responds to a single topic with the DXL fabric:
 * </P>
 * <PRE>
 * RequestCallback myRequestCallback =
 *     request -&gt; {
 *         try {
 *             // Extract information from request
 *             System.out.println("Service received request payload: "
 *                 + new String(request.getPayload(), Message.CHARSET_UTF8));
 *
 *             // Create the response message
 *             final Response res = new Response(request);
 *
 *             // Populate the response payload
 *             res.setPayload("pong".getBytes(Message.CHARSET_UTF8));
 *
 *             // Send the response
 *             client.sendResponse(res);
 *         } catch (Exception ex) {
 *             ex.printStackTrace();
 *         }
 *     };
 *
 * // Create service registration object
 * ServiceRegistrationInfo info = new ServiceRegistrationInfo(client, "myService");
 *
 * // Add a topic for the service to respond to
 * info.addTopic("/mycompany/myservice", myRequestCallback);
 *
 * // Register the service with the fabric (wait up to 10 seconds for registration to complete)
 * client.registerServiceSync(info, 10 * 1000);
 * </PRE>
 * <P>
 * The following demonstrates a client that is invoking the service in the example above:
 * </P>
 * <PRE>
 * // Create the request message
 * final Request req = new Request("/mycompany/myservice");
 *
 * // Populate the request payload
 * req.setPayload("ping".getBytes(Message.CHARSET_UTF8));
 *
 * // Send the request and wait for a response (synchronous)
 * final Response res = client.syncRequest(req);
 *
 * // Extract information from the response (output error message if applicable)
 * if (res.getMessageType() != Message.MESSAGE_TYPE_ERROR) {
 *     System.out.println("Client received response payload: "
 *         + new String(res.getPayload(), Message.CHARSET_UTF8));
 * } else {
 *     System.out.println("Error: " + ((ErrorResponse) res).getErrorMessage());
 * }
 * </PRE>
 */
public class ServiceRegistrationInfo {

    /**
     * The service type or name prefix
     */
    private final String serviceType;

    /**
     * The unique service identifier
     */
    private final String serviceId;

    /**
     * The map of registered topics and their associated callbacks
     */
    private final Map<String, Set<RequestCallback>> callbacksByTopic = new HashMap<>();

    /**
     * The map of meta-data associated with this service (name-value pairs)
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
     * Constructor for {@link ServiceRegistrationInfo}
     *
     * @param client The {@link DxlClient} instance that will expose this service
     * @param serviceType  textual name for the service. For example, "/mycompany/myservice"
     */
    public ServiceRegistrationInfo(final DxlClient client, final String serviceType) {
        if (serviceType == null || serviceType.isEmpty()) {
            throw new IllegalArgumentException("Undefined service name");
        }

        this.serviceType = serviceType;
        this.serviceId = UuidGenerator.generateIdAsString();
        this.ttlMins = Math.max(1, this.ttl / this.ttlResolution);
        this.client = client;
    }

    /**
     * Returns the a textual name for the service. For example, "/mycompany/myservice"
     *
     * @return A textual name for the service. For example, "/mycompany/myservice"
     */
    public String getServiceType() {
        return serviceType;
    }

    /**
     * Returns a unique identifier for the service instance (automatically generated when the
     * {@link ServiceRegistrationInfo} object is constructed
     *
     * @return A unique identifier for the service instance (automatically generated when the
     *      {@link ServiceRegistrationInfo} object is constructed
     */
    public String getServiceId() {
        return serviceId;
    }

    /**
     * Returns the interval (in minutes) at which the client will automatically re-register the service with the DXL
     * fabric (defaults to {@code 60} minutes).
     *
     * @return The interval (in minutes) at which the client will automatically re-register the service with the DXL
     *      fabric (defaults to {@code 60} minutes).
     */
    public long getTtlMins() {
        return ttlMins;
    }

    /**
     * Returns the interval (in minutes unless the resolution has been modified) at which the client will automatically
     * re-register the service with the DXL fabric (defaults to {@code 60} minutes).
     *
     * @return The interval (in minutes unless the resolution has been modified) at which the client will
     *      automatically re-register the service with the DXL fabric (defaults to {@code 60} minutes).
     */
    public long getTtl() {
        return ttl;
    }

    /**
     * Sets the interval (in minutes unless the resolution has been modified) at which the client will automatically
     * re-register the service with the DXL fabric (defaults to {@code 60} minutes).
     *
     * @param ttl The interval (in minutes unless the resolution has been modified) at which the client will
     *            automatically re-register the service with the DXL fabric (defaults to {@code 60} minutes).
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
     * Returns the Time-To-Live (TTL) resolution factor (by default is {@code 1}, minutes)
     *
     * @return The Time-To-Live (TTL) resolution factor (by default is {@code 1}, minutes)
     */
    public long getTtlResolution() {
        return ttlResolution;
    }

    /**
     * Returns a {@link Set} containing the topics that the service responds to
     *
     * @return A {@link Set} containing the topics that the service responds to
     */
    public Set<String> getTopics() {
        return new HashSet<>(callbacksByTopic.keySet());
    }

    /**
     * Returns the a {@link Map} containing the {@link RequestCallback} instances by their associated topic.
     *
     * @return A {@link Map} containing the {@link RequestCallback} instances by their associated topic.
     */
    public Map<String, Set<RequestCallback>> getCallbacksByTopic() {
        return callbacksByTopic;
    }

    /**
     * Registers a topic for the service to respond to along with the {@link RequestCallback} that will be invoked.
     *
     * @param topic The topic for the service to respond to
     * @param callback The {@link RequestCallback} that will be invoked when a {@link Request} message is received
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
     * Removes a request topic and associated callback from the service.
     *
     * @param topic The topic to remove
     * @param callback The {@link RequestCallback} to remove
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
     * A {@link Map} of name-value pairs that are sent as part of the service registration. Brokers provide a registry
     * service that allows for registered services and their associated meta-information to be inspected. The meta-data
     * is typically used to include information such as the versions for products that are exposing DXL services, etc.
     *
     * @param metadata A {@link Map} of name-value pairs that are sent as part of the service registration.
     */
    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    /**
     * Adds a name-value pair to the meta-data associated with this service
     *
     * @param name Name of the meta-data property
     * @param value Value of the meta-data property
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
     * @param waitTime The amount of time to wait
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
     * Returns the set of tenant identifiers that the service will be available to. Setting this value will limit which
     * tenants can invoke the service.
     *
     * @return The set of tenant identifiers that the service will be available to. Setting this value will limit which
     * tenants can invoke the service.
     */
    public Set<String> getDestTenantGuids() {
        return destTenantGuids;
    }

    /**
     * Sets the tenant identifiers that the service will be available to. Setting this value will limit which
     * tenants can invoke the service.
     *
     * @param destTenantGuids The set of tenant identifiers that the service will be available to
     */
    public void setDestTenantGuids(final Set<String> destTenantGuids) {
        this.destTenantGuids = destTenantGuids;
    }
}
