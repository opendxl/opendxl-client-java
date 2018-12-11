/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client;

import com.opendxl.client.callback.RequestCallback;
import com.opendxl.client.exception.DxlException;
import com.opendxl.client.message.DxlErrorMessageEnum;
import com.opendxl.client.message.ErrorResponse;
import com.opendxl.client.message.Request;
import org.apache.log4j.Logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Service registration manager for the Data Exchange Layer (DXL) fabric.
 */
class ServiceManager implements RequestCallback, AutoCloseable {
    /**
     * The logger
     */
    private static Logger log = Logger.getLogger(ServiceManager.class);

    /**
     * Read write lock for handling registration and firing concurrency
     */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * Map containing registered services
     */
    private final Map<String, ServiceRegistrationHandler> services = new HashMap<>();

    /**
     * cloned Map of services
     */
    private Map<String, ServiceRegistrationHandler> clonedServices = Collections.EMPTY_MAP;

    /**
     * The client that the service manager is associated with
     */
    private DxlClient client;

    /**
     * Creates the service manager
     *
     * @param client client that the service manager is associated with
     */
    ServiceManager(final DxlClient client) {
        if (client == null) {
            throw new IllegalArgumentException("Undefined client object");
        }

        this.client = client;
    }

    /**
     * Adds the specified service.
     *
     * @param service The service to add
     */
    void addService(final ServiceRegistrationInfo service)
        throws DxlException {
        if (service == null) {
            throw new IllegalArgumentException("Undefined service object");
        }
        lock.writeLock().lock();
        try {
            boolean isUpdate = false;
            @SuppressWarnings("unchecked") Set<String> oldTopics = Collections.EMPTY_SET;
            ServiceRegistrationHandler serviceHandler = services.get(service.getServiceGuid());
            if (serviceHandler == null) {
                // Register the service
                serviceHandler = new ServiceRegistrationHandler(client, service);
                // Add the service handler to the map
                services.put(service.getServiceGuid(), serviceHandler);
                clonedServices = new HashMap<>(services);
            } else {
                final ServiceRegistrationInfo oldInfo = serviceHandler.getService();
                if (oldInfo == null || oldInfo != service) {
                    throw new IllegalArgumentException("Service already registered");
                }

                isUpdate = true;
                oldTopics = serviceHandler.getRequestTopics();
                serviceHandler.updateService();
            }

            // Register the generic callback(s) and subscribe to the topic(s)
            final Set<String> newTopics = service.getTopics();
            for (String channel : newTopics) {
                if (!isUpdate || !oldTopics.contains(channel)) {
                    client.addRequestCallback(channel, this);
                    client.subscribe(channel);
                }
            }

            if (isUpdate) {
                final Set<String> removedChannels = new HashSet<>(oldTopics);
                removedChannels.removeAll(newTopics);

                for (final String channelToRemove : removedChannels) {
                    client.removeRequestCallback(channelToRemove, this);
                    client.unsubscribe(channelToRemove);
                }

                try {
                    if (client.isConnected()) {
                        serviceHandler.sendRegisterServiceRequest();
                    }
                } catch (Exception ex) {
                    log.error("Error sending service registration request", ex);
                }
            } else {
                // Start the TTL timer if the client is currently connected
                if (client.isConnected()) {
                    serviceHandler.startTimer();
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Removes the specified service.
     *
     * @param instanceId The instance ID of the service to remove
     */
    void removeService(final String instanceId)
        throws DxlException {
        if (instanceId == null || instanceId.isEmpty()) {
            throw new IllegalArgumentException("Undefined service instance ID");
        }

        lock.writeLock().lock();
        try {
            ServiceRegistrationHandler handler = services.get(instanceId);
            if (handler == null) {
                throw new DxlException("Service instance ID unknown: " + instanceId);
            }

            // Stop the TTL timer
            handler.stopTimer();

            // Unsubscribe the topic(s) and unregister the generic callback
            for (String topic : handler.getRequestTopics()) {
                // This was the reason for the reference counter in the MessageCallback
                // If we subscribe the same service topic twice, unregistering one of
                // the services would unregister them all.  Hence, we need to check for
                // the topic not being in use otherwise.
                if (!hasActiveTopic(topic)) {
                    client.unsubscribe(topic);
                    client.removeRequestCallback(topic, this);
                }
            }

            // Mark the service for deletion
            handler.markForDeletion();

            // If the client is actually connected, send unregister event. Remove upon success.
            if (client.isConnected()) {
                try {
                    handler.sendUnregisterServiceEvent();
                    services.remove(instanceId);
                    clonedServices = new HashMap<>(services);
                } catch (Exception ex) {
                    log.error("Error sending unregister service event for "
                        + handler.getServiceType() + " ("
                        + handler.getInstanceId() + "), service marked for deletion: " + ex.getMessage());
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Returns the set of all active topics
     *
     * @return The set of all active topics
     */
    private Set<String> getActiveTopics() {
        Set<String> topics = new HashSet<>();
        lock.readLock().lock();
        try {
            for (ServiceRegistrationHandler handler : services.values()) {
                if (!handler.isDeleted()) {
                    topics.addAll(handler.getRequestTopics());
                }
            }
        } finally {
            lock.readLock().unlock();
        }
        return topics;
    }

    /**
     * Returns true if the set of active topics contains the given topic
     *
     * @param topic The topic to check for
     * @return true if the set of active topics contains the given topic, otherwise false
     */
    private boolean hasActiveTopic(String topic) {
        return getActiveTopics().contains(topic);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onRequest(final Request request) {
        String serviceInstanceId = request.getServiceGuid();

        // If no instance ID available, send to all registered instances
        //Bug 1228290 - SIA Partner Extension Tycon Rapid Query hangs indefinitely on check-in
        if (serviceInstanceId == null || serviceInstanceId.isEmpty()) {
            for (ServiceRegistrationHandler service : clonedServices.values()) {
                commonOnRequest(service, request);
            }
        // Find the service by its instance ID; Handle request if found, otherwise ignore
        } else {
            final ServiceRegistrationHandler service = clonedServices.get(serviceInstanceId);
            if (service != null) {
                commonOnRequest(service, request);
            } else {
                log.warn("No service with GUID " + serviceInstanceId + " registered. Ignoring request.");
                sendServiceNotFoundErrorMessage(request);
            }
        }
    }

    /**
     * Sens a service not found error message response
     *
     * @param request The request
     */
    private void sendServiceNotFoundErrorMessage(final Request request) {
        final ErrorResponse errorResponse =
            new ErrorResponse(this.client, request,
                DxlErrorMessageEnum.FABRICSERVICEUNAVAILABLE.getErrorCode(),
                DxlErrorMessageEnum.FABRICSERVICEUNAVAILABLE.getErrorMessage());

        try {
            this.client.sendResponse(errorResponse);
        } catch (final Exception ex) {
            log.error("Error sending service not found error message", ex);
        }
    }

    private void commonOnRequest(final ServiceRegistrationHandler service, final Request request) {
        try {
            service.getRequestCallbacks().fireMessage(request);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
    }

    /**
     * On connect, check for deleted and/or rogue services and send unregister event, if necessary.
     * Start timer threads for all active services.
     */
    void onConnect() {
        lock.writeLock().lock();
        try {
            Iterator<Map.Entry<String, ServiceRegistrationHandler>> iter = services.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<String, ServiceRegistrationHandler> entry = iter.next();
                ServiceRegistrationHandler handler = entry.getValue();
                if (handler.isDeleted()) {
                    try {
                        handler.sendUnregisterServiceEvent();
                        iter.remove();
                    } catch (Exception ex) {
                        log.error("Error sending unregister service event for "
                            + handler.getServiceType() + " ("
                            + handler.getInstanceId() + "): " + ex.getMessage());
                    }
                } else {
                    try {
                        handler.startTimer();
                    } catch (DxlException ex) {
                        log.error("Failed to start timer thread for service "
                            + handler.getServiceType() + " ("
                            + handler.getInstanceId() + "): " + ex.getMessage());
                    }
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * On disconnect, send unregister event for all active services as long as still connected.
     * Stop all timer threads.
     */
    void onDisconnect() {
        lock.readLock().lock();
        try {
            for (ServiceRegistrationHandler handler : services.values()) {
                // Send unregister event for all active services
                if (client.isConnected()) {
                    try {
                        handler.sendUnregisterServiceEvent();
                    } catch (Exception ex) {
                        log.error("Error sending unregister service event for "
                            + handler.getServiceType() + " ("
                            + handler.getInstanceId() + "): " + ex.getMessage());
                    }
                }
                handler.stopTimer();
            }
        } finally {
            lock.readLock().unlock();
        }
    }
}
