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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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
     * Read-write lock for handling registration and firing concurrency
     */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * Map containing registered services
     */
    private final Map<String, ServiceRegistrationHandler> services = new HashMap<>();

    /**
     * cloned {@link Map} of services
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
            ServiceRegistrationHandler serviceHandler = services.get(service.getServiceId());
            if (serviceHandler == null) {
                // Register the service
                serviceHandler = new ServiceRegistrationHandler(client, service);
                // Add the service handler to the map
                services.put(service.getServiceId(), serviceHandler);
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
            for (String topic : newTopics) {
                if (!isUpdate || !oldTopics.contains(topic)) {
                    client.addRequestCallback(topic, this);
                    try {
                        client.subscribe(topic);
                    } catch (Exception ex) {
                        log.error("Exception during subscribe in service registration for channel:" + topic, ex);
                    }
                }
            }

            if (isUpdate) {
                final Set<String> removedTopics = new HashSet<>(oldTopics);
                removedTopics.removeAll(newTopics);

                for (final String topicToRemove : removedTopics) {
                    client.removeRequestCallback(topicToRemove, this);
                    try {
                        client.unsubscribe(topicToRemove);
                    } catch (Exception ex) {
                        log.error("Exception during unsubscribe in service registration for channel:"
                                + topicToRemove, ex);
                    }
                }

                try {
                    if (client.isConnected()) {
                        try {
                            serviceHandler.sendRegisterServiceRequest();
                        } catch (Exception ex) {
                            log.error(
                                    "Exception during sendRegisterServiceRequest in service registration for service:"
                                            + service.getServiceType(), ex);
                        }
                    }
                } catch (Exception ex) {
                    log.error("Error sending service registration request", ex);
                }
            } else {
                // Start the TTL timer if the client is currently connected
                if (client.isConnected()) {
                    try {
                        serviceHandler.startTimer();
                    } catch (Exception ex) {
                        log.error("Exception during startTimer in service registration for service:"
                                + service.getServiceType(), ex);
                    }
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Removes the specified service.
     *
     * @param instanceId The instance identifier of the service to remove
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
                    try {
                        client.unsubscribe(topic);
                    } catch (Exception ex) {
                        log.error("Exception during unsubscribe in service unregistration for channel:" + topic, ex);
                    }
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
     * Returns the {@link Set} of all active topics
     *
     * @return The {@link Set} of all active topics
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
     * Returns {@code true} if the set of active topics contains the given topic
     *
     * @param topic The topic to check for
     * @return {@code true} if the set of active topics contains the given topic, otherwise {@code false}
     */
    private boolean hasActiveTopic(String topic) {
        return getActiveTopics().contains(topic);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onRequest(final Request request) {
        String serviceInstanceId = request.getServiceId();

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
     * Sends a service not found error message response
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
        // Unregister services
        onDisconnect();
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

    /**
     * Returns information regarding active services
     *
     * @return Information regarding active services
     */
    List<Map<String, Object>> getActiveServices() {
        List<Map<String, Object>> results = new ArrayList<>();
        lock.readLock().lock();
        try {
            for (ServiceRegistrationHandler handler : services.values()) {
                if (!handler.isDeleted()) {
                    final Map<String, Object> service = new HashMap<>();
                    service.put("type", handler.getServiceType());
                    service.put("guid", handler.getInstanceId());
                    service.put("topics", new TreeSet<>(handler.getRequestTopics()));
                    service.put("metadata", handler.getMetadata());
                    service.put("ttl", handler.getTtlMins());
                    service.put("registration", (handler.getRegisterTimeMillis() / 1000));
                    results.add(service);
                }
            }
        } finally {
            lock.readLock().unlock();
        }

        return results;
    }

}
