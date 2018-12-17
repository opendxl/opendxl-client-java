/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client;

import com.opendxl.client.callback.EventCallback;
import com.opendxl.client.callback.MessageCallback;
import com.opendxl.client.callback.RequestCallback;
import com.opendxl.client.callback.ResponseCallback;
import com.opendxl.client.message.Event;
import com.opendxl.client.message.Message;
import com.opendxl.client.message.Request;
import com.opendxl.client.message.Response;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Manager that handles the registration and firing of messages to registered message callbacks (requests, responses,
 * events, etc.).
 *
 * @param <T>  The type of message callback being managed (request callback, response callback, etc.).
 * @param <MT> The type of message being managed (request, response, etc.)
 * @see RequestCallback
 * @see ResponseCallback
 * @see EventCallback
 * @see Request
 * @see Response
 * @see Event
 */
abstract class CallbackManager<T extends MessageCallback, MT extends Message> {

    /**
     * Read-write lock for handling registration and firing concurrency
     */
    private final ReadWriteLock managerLock = new ReentrantReadWriteLock();

    /**
     * Map containing registered listeners by topic name ({@code null} indicates all topics)
     */
    private final Map<String, Set<T>> callbacksByTopic = new HashMap<>();

    /**
     * Whether topic wild carding is enabled
     */
    private boolean wildcardingEnabled = false;

    /**
     * Returns whether the topic has a wildcard
     *
     * @param topicName The topic name
     * @return Whether the topic has a wildcard
     */
    private boolean hasWildcard(final String topicName) {
        return topicName != null && topicName.endsWith("#");
    }

    /**
     * Adds the specified callback. Since no topic has been specified, this callback will receive all messages
     * (no topic filtering).
     *
     * @param callback The callback to add
     */
    public void addCallback(final T callback) {
        addCallback(null, callback);
    }

    /**
     * Adds the specified callback. The callback will receive messages that were received via the specified topic.
     *
     * @param topicName Limits messages sent to the callback that were received via this topic.
     * @param callback The callback to add
     */
    public void addCallback(final String topicName, final T callback) {
        managerLock.writeLock().lock();
        try {
            if (hasWildcard(topicName)) {
                this.wildcardingEnabled = true;
            }

            Set<T> callbacks = this.callbacksByTopic.get(topicName);
            //noinspection Java8MapApi
            if (callbacks == null) {
                callbacks = new HashSet<>();
                this.callbacksByTopic.put(topicName, callbacks);
            }
            callbacks.add(callback);
        } finally {
            managerLock.writeLock().unlock();
        }
    }

    /**
     * Removes the callback that was registered without a specific topic (no topic filtering).
     *
     * @param callback The callback to remove
     * @return If the callback was removed successfully
     */
    public boolean removeCallback(final T callback) {
        return removeCallback(null, callback);
    }

    /**
     * Removes the callback that was registered for the specified topic.
     *
     * @param topicName The topic name
     * @param callback The callback to remove
     * @return If the callback was removed successfully
     */
    boolean removeCallback(final String topicName, final T callback) {
        managerLock.writeLock().lock();
        try {
            Set<T> callbacks = this.callbacksByTopic.get(topicName);
            if (callbacks != null) {
                if (callbacks.remove(callback)) {
                    if (callbacks.size() == 0) {
                        this.callbacksByTopic.remove(topicName);
                    }

                    return true;
                }
            }

            // Determine if any wildcards exist
            if (this.wildcardingEnabled) {
                this.wildcardingEnabled = false;
                for (final String currTopicName : this.callbacksByTopic.keySet()) {
                    if (hasWildcard(currTopicName)) {
                        this.wildcardingEnabled = true;
                        break;
                    }
                }
            }
        } finally {
            managerLock.writeLock().unlock();
        }

        return false;
    }

    /**
     * Fires the specified message to the appropriate (taking into consideration topic) registered listeners.
     *
     * @param message The message to fire
     */
    void fireMessage(final MT message) {
        final String destinationTopic = message.getDestinationTopic();

        Set<T> globalListeners;
        Set<T> topicListeners;

        managerLock.readLock().lock();
        try {
            globalListeners = this.callbacksByTopic.get(null);
            topicListeners = this.callbacksByTopic.get(destinationTopic);
        } finally {
            managerLock.readLock().unlock();
        }

        // Fire for global listeners (null)
        commonFireMessage(
            globalListeners, message);
        // Fire for topic listeners
        commonFireMessage(
            topicListeners, message);

        // Fire for all wildcarded topics
        if (this.wildcardingEnabled) {
            DxlUtils.iterateWildcards(
                wildcard -> {
                    Set<T> wildCardListeners;
                    managerLock.readLock().lock();
                    try {
                        wildCardListeners = this.callbacksByTopic.get(wildcard);
                    } finally {
                        managerLock.readLock().unlock();
                    }
                    commonFireMessage(wildCardListeners, message);
                },
                destinationTopic
            );
        }
    }

    /**
     * Fires the message to the specified set of callbacks
     *
     * @param callbacks The callbacks to fire the message to
     * @param message The message to fire
     */
    private void commonFireMessage(final Set<T> callbacks, final MT message) {
        if (callbacks == null || callbacks.isEmpty()) {
            return;
        }

        for (T cb : callbacks) {
            handleFire(cb, message);
        }
    }

    /**
     * Method that is to be overridden by derived classes to fire the message to the specified listener.
     *
     * @param listener The target of the message
     * @param message The message
     */
    protected abstract void handleFire(T listener, MT message);

    /**
     * Manager for {@link RequestCallback} message listeners.
     */
    public static class RequestCallbackManager extends CallbackManager<RequestCallback, Request> {
        /**
         * {@inheritDoc}
         */
        @Override
        protected void handleFire(final RequestCallback listener, final Request message) {
            listener.onRequest(message);
        }
    }

    /**
     * Manager for {@link ResponseCallback} message listeners.
     */
    public static class ResponseCallbackManager extends CallbackManager<ResponseCallback, Response> {
        /**
         * {@inheritDoc}
         */
        @Override
        protected void handleFire(final ResponseCallback listener, final Response message) {
            listener.onResponse(message);
        }
    }

    /**
     * Manager for {@link EventCallback} message listeners.
     */
    public static class EventCallbackManager extends CallbackManager<EventCallback, Event> {
        /**
         * {@inheritDoc}
         */
        @Override
        protected void handleFire(final EventCallback listener, final Event message) {
            listener.onEvent(message);
        }
    }
}
