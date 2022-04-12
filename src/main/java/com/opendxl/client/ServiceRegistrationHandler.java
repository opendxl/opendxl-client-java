/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.opendxl.client.callback.RequestCallback;
import com.opendxl.client.exception.DxlException;
import com.opendxl.client.message.ErrorResponse;
import com.opendxl.client.message.Message;
import com.opendxl.client.message.Request;
import com.opendxl.client.message.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.apache.commons.codec.Charsets.UTF_8;

/**
 * Service registration handler for the Data Exchange Layer (DXL) fabric.
 */
class ServiceRegistrationHandler {
    /**
     * The logger
     */
    private static Logger log = LogManager.getLogger(ServiceRegistrationHandler.class);

    /**
     * Read write lock for handling delete flag and registration time
     */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * The Time-To-Live (TTL) grace period of the service registration (default: 10 minutes)
     */
    private long ttlGracePeriod =
        Long.parseLong(System.getProperty(Constants.SYSPROP_SERVICE_TTL_GRACE_PERIOD, Long.toString(10)));

    /**
     * The service type or name prefix
     */
    private final String serviceType;
    /**
     * The service instance ID
     */
    private final String instanceId;
    /**
     * The list of full qualified registered topics
     */
    private Set<String> requestTopics;
    /**
     * The map of meta data associated with this service (name-value pairs)
     */
    private Map<String, String> metadata;
    /**
     * The Time-To-Live (TTL) of the service registration (in minutes)
     */
    private final long ttlMins;
    /**
     * The Time-To-Live (TTL) of the service registration
     */
    private final long ttl;
    /**
     * The Time-To-Live (TTL) resolution factor
     */
    private final long ttlResolution;

    /**
     * The service registration info
     */
    private final ServiceRegistrationInfo serviceRegInfo;

    /**
     * The internal client reference
     */
    private final DxlClient client;

    /**
     * The request callback manager
     */
    private CallbackManager.RequestCallbackManager requestCallbacks =
        new CallbackManager.RequestCallbackManager();

    /**
     * The last time the service has been registered successfully (ms)
     */
    private long registerTimeMillis = 0L;
    /**
     * The internal flag for a service registration to be deleted
     */
    private boolean deleted = false;
    /**
     * The set of tenants that the service will be available to
     */
    @SuppressWarnings("unchecked")
    private Set<String> destTenantGuids = Collections.EMPTY_SET;
    /**
     * Registration thread
     */
    private RegistrationThread registrationThread;

    /**
     * Constructs the ServiceRegistrationHandler object
     *
     * @param client  The internal client reference
     * @param service The service registration info
     */
    ServiceRegistrationHandler(final DxlClient client, final ServiceRegistrationInfo service)
        throws DxlException {
        // Hold onto all the data we need for the register and unregister events
        this.serviceType = service.getServiceType();
        this.instanceId = service.getServiceId();
        this.ttlMins = service.getTtlMins();
        this.ttl = service.getTtl();
        this.ttlResolution = service.getTtlResolution();
        this.destTenantGuids = service.getDestTenantGuids();

        // Set reference to client
        this.client = client;

        // Create a weak reference to the service registration info
        this.serviceRegInfo = service;

        // Update state associated with the service
        updateService();
    }

    /**
     * Updates the service associated with the registration handler
     *
     * @throws DxlException If an error occurs
     */
    synchronized void updateService() throws DxlException {
        ServiceRegistrationInfo service = getService();
        if (service == null) {
            throw new DxlException("Service no longer valid");
        }

        // Add the callbacks
        CallbackManager.RequestCallbackManager cbManager = new CallbackManager.RequestCallbackManager();
        for (Map.Entry<String, Set<RequestCallback>> cb : service.getCallbacksByTopic().entrySet()) {
            String topic = cb.getKey();
            Set<RequestCallback> callbacks = cb.getValue();
            if (callbacks != null) {
                for (RequestCallback callback : callbacks) {
                    if (callback != null) {
                        cbManager.addCallback(topic, callback);
                    }
                }
            }
        }

        requestCallbacks = cbManager;
        requestTopics = new HashSet<>(service.getCallbacksByTopic().keySet());
        metadata = (service.getMetadata() == null || service.getMetadata().isEmpty() ? null
            : new HashMap<>(service.getMetadata()));
    }

    /**
     * Returns the service type or name prefix
     *
     * @return The service type or name prefix
     */
    public String getServiceType() {
        return serviceType;
    }

    /**
     * Returns the service instance ID
     *
     * @return The service instance ID
     */
    public String getInstanceId() {
        return instanceId;
    }

    /**
     * Returns The list of full qualified registered topics
     *
     * @return The list of full qualified registered topics
     */
    public Set<String> getRequestTopics() {
        return requestTopics;
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
     * Returns the Time-To-Live (TTL) resolution factor
     *
     * @return The Time-To-Live (TTL) resolution factor
     */
    public long getTtlResolution() {
        return ttlResolution;
    }

    /**
     * Returns the internal client reference
     *
     * @return The internal client reference
     */
    public DxlClient getClient() {
        return client;
    }

    /**
     * Returns the service registration info
     *
     * @return The service registration info
     */
    public synchronized ServiceRegistrationInfo getService() {
        return this.serviceRegInfo;
    }

    /**
     * Returns the request callback manager
     *
     * @return The request callback manager
     */
    CallbackManager.RequestCallbackManager getRequestCallbacks() {
        return requestCallbacks;
    }

    /**
     * Send the registration request for the service
     */
    void sendRegisterServiceRequest()
        throws JsonProcessingException, DxlException {
        if (client == null) {
            throw new DxlException("Client not defined");
        }

        Request request = new Request(client, Constants.DXL_SERVICE_REGISTER_REQUEST_TOPIC);

        request.setDestTenantGuids(destTenantGuids);
        // Ensure our state does not change when serializing
        JsonRegisterService json;
        synchronized (this) {
            json =
                new JsonRegisterService(
                    client,
                    this.serviceType,
                    this.instanceId,
                    this.requestTopics,
                    this.metadata,
                    this.ttlMins);
        }

        log.debug("Sending registration request\n" + json.toPrettyJsonString());

        request.setPayload(json.toJsonString().getBytes(UTF_8)); //TODO Change this to use standard charsets from java
        final Response response = client.syncRequest(request); // Synchronous?
        if (response.getMessageType() != Message.MESSAGE_TYPE_ERROR) {
            updateRegisterTimeMillis();
            // Notify that the registration succeeded
            final ServiceRegistrationInfo info = getService();
            if (info != null) {
                info.notifyRegistrationSucceeded();
            }
        } else {
            final ErrorResponse errResponse = (ErrorResponse) response;
            log.error("Error registering service: " + errResponse.getErrorMessage());
        }
    }

    /**
     * Send the unregister event for the service
     */
    void sendUnregisterServiceEvent()
        throws JsonProcessingException, DxlException {
        if (client == null) {
            throw new DxlException("Client not defined");
        }

        // Send the unregister event only if the register event was sent before and TTL has not yet expired.
        // The grace period is added to the given TTL.
        Long currentTimeMillis = System.currentTimeMillis();
        Long lastRegisterTimeMillis = getRegisterTimeMillis();
        if (lastRegisterTimeMillis > 0 && ((currentTimeMillis - lastRegisterTimeMillis) / 1000L)
            <= ((ttl + ttlGracePeriod) * (60L / ttlResolution))) {
            Request request = new Request(client, Constants.DXL_SERVICE_UNREGISTER_REQUEST_TOPIC);
            JsonUnregisterService json = new JsonUnregisterService(instanceId);

            log.debug("Sending unregistration request\n" + json.toPrettyJsonString());

            request.setPayload(json.toJsonString().getBytes(UTF_8)); //TODO see other comment about charsets
            final Response response = client.syncRequest(request, 60 * 1000); // Wait a minute max
            if (response.getMessageType() != Message.MESSAGE_TYPE_ERROR) {
                // Notify that the unregistration succeeded
                final ServiceRegistrationInfo info = getService();
                if (info != null) {
                    info.notifyUnregistrationSucceeded();
                }
            } else {
                final ErrorResponse errResponse = (ErrorResponse) response;
                log.error("Error unregistering service: " + errResponse.getErrorMessage());
            }
        } else {
            if (lastRegisterTimeMillis > 0) {
                log.info("TTL expired; unregister service event omitted for "
                    + serviceType + " (" + instanceId + ")");
            }
        }
    }

    /**
     * Starts the TTL timer task
     */
    void startTimer()
        throws DxlException {
        if (client == null) {
            throw new DxlException("Client not defined");
        }

        if (client.isConnected() && !deleted) {
            if (registrationThread == null) {
                registrationThread = new RegistrationThread();
                registrationThread.start();
            } else {
                registrationThread.wakeup();
            }
        }
    }

    /**
     * Stops the TTL timer task
     */
    void stopTimer() {
        if (registrationThread != null) {
            registrationThread.setRunning(false);
            try {
                registrationThread.join();
                registrationThread = null;
            } catch (Exception ex) {
                log.error("Error waiting for registration thread to stop", ex);
            }
        }
    }

    /**
     * Returns true if the service is marked for deletion
     *
     * @return True if the service is marked for deletion, otherwise false
     */
    boolean isDeleted() {
        lock.readLock().lock();
        try {
            return deleted;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Marks the service for deletion
     */
    void markForDeletion() {
        lock.writeLock().lock();
        try {
            deleted = true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Wakes up the timer
     */
    void wakeupTimer() {
        if (registrationThread != null) {
            registrationThread.wakeup();
        }
    }

    /**
     * Returns the last registration time in milliseconds
     *
     * @return The last registration time in milliseconds, or 0L if not registered
     */
    long getRegisterTimeMillis() {
        lock.readLock().lock();
        try {
            return registerTimeMillis;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Updates the last registration time in milliseconds
     */
    private void updateRegisterTimeMillis() {
        lock.writeLock().lock();
        try {
            registerTimeMillis = System.currentTimeMillis();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * The registration thread
     */
    class RegistrationThread extends Thread {

        /** The thread notification object */
        private final Object registrationThreadNotify = new Object();
        /** Whether the thread is running */
        private boolean running = true;
        /** Track the notify count */
        private long notifyCount = 0;

        /**
         * Cosntructs the registration thread
         */
        RegistrationThread() {
            super();
            setName("Service registration thread: "
                + this.hashCode() + ", "
                + ServiceRegistrationHandler.this.getServiceType() + ", "
                + ServiceRegistrationHandler.this.getInstanceId());
            setDaemon(true);
        }

        /**
         * Sets the running state of the registration thread
         *
         * @param running Whether the registration thread should run
         */
        public void setRunning(final boolean running) {
            synchronized (registrationThreadNotify) {
                this.running = running;
                registrationThreadNotify.notifyAll();
            }
        }

        /**
         * Wakes up the registration thread
         */
        void wakeup() {
            synchronized (registrationThreadNotify) {
                this.notifyCount++;
                registrationThreadNotify.notifyAll();
            }
        }

        /** {@inheritDoc} */
        @Override
        public void run() {
            final long waitTime = (ttl * (60L / ttlResolution) * 1000L);
            long lastNotifyCount = 0;
            while (running) {
                if (log.isDebugEnabled()) {
                    log.debug("Registration thread running: " + this.getName());
                }

                if (client.isConnected() && !deleted) {
                    try {
                        sendRegisterServiceRequest();
                    } catch (Exception ex) {
                        log.error("Error sending register service event for "
                            + serviceType + " (" + instanceId + "): " + ex.getMessage());
                    }
                }

                synchronized (registrationThreadNotify) {
                    log.debug("Registration thread notify count " + this.notifyCount + ", " + lastNotifyCount);
                    if (this.notifyCount == lastNotifyCount) {
                        try {
                            if (running) {
                                if (log.isDebugEnabled()) {
                                    log.debug("Registration thread waiting: " + waitTime + ", " + this.getName());
                                }
                                registrationThreadNotify.wait(waitTime);
                            }
                        } catch (Exception ex) {
                            log.error("Error during registration thread wait", ex);
                        }
                    } else {
                        lastNotifyCount = this.notifyCount;
                    }
                }
            }
            if (log.isDebugEnabled()) {
                log.debug("Registration thread exiting: " + this.getName());
            }
        }
    }
}
