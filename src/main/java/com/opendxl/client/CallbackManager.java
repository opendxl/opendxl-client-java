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
import com.opendxl.client.util.DxlUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Manager that handles the registration and firing of messages to registered message
 * callbacks (requests, responses, events, etc.).
 *
 * @param <T>  The type of message callback managed by the manager instance
 *             (request callback, response callback, etc.
 * @param <MT> The type of message fired by the manager instance (request, response, etc.)
 * @see RequestCallback
 * @see ResponseCallback
 * @see EventCallback
 * @see Request
 * @see Response
 * @see Event
 */
abstract class CallbackManager<T extends MessageCallback, MT extends Message> {
    /**
     * Read write lock for handling registration and firing concurrency
     */
    private final ReadWriteLock managerLock = new ReentrantReadWriteLock();
    /**
     * Map containing registered listeners by channel name (null indicates all channels)
     */
    private final Map<String, Set<T>> callbacksByChannel = new HashMap<>();
    /**
     * Is wildcarding enabled
     */
    private boolean wildcardingEnabled = false;

    /**
     * Returns whether the channel has a wildcard
     *
     * @param channelName The channel name
     * @return Whether the channel has a wildcard
     */
    private boolean hasWildcard(final String channelName) {
        return channelName != null && channelName.endsWith("#");
    }

    /**
     * Adds the specified callback. Since no channel has been specified, this callback will
     * receive all messages (no channel filtering).
     *
     * @param callback The callback to add
     */
    public void addCallback(final T callback) {
        addCallback(null, callback);
    }

    /**
     * Adds the specified callback. The callback will receive messages that were received
     * via the specified channel.
     *
     * @param channelName Limits messages sent to the callback that were received via
     *                    this channel.
     * @param callback    The callback to add
     */
    public void addCallback(final String channelName, final T callback) {
        managerLock.writeLock().lock();
        try {
            if (hasWildcard(channelName)) {
                this.wildcardingEnabled = true;
            }

            Set<T> callbacks = this.callbacksByChannel.get(channelName);
            //noinspection Java8MapApi
            if (callbacks == null) {
                callbacks = new HashSet<>();
                this.callbacksByChannel.put(channelName, callbacks);
            }
            callbacks.add(callback);
        } finally {
            managerLock.writeLock().unlock();
        }
    }

    /**
     * Removes the callback that was registered without a specific channel (no
     * channel filtering).
     *
     * @param callback The callback to remove
     * @return If the callback was removed successfully
     */
    public boolean removeCallback(final T callback) {
        return removeCallback(null, callback);
    }

    /**
     * Removes the callback that was registered for the specified channel.
     *
     * @param channelName The channel name
     * @param callback    The callback to remove
     * @return If the callback was removed successfully
     */
    boolean removeCallback(final String channelName, final T callback) {
        managerLock.writeLock().lock();
        try {
            Set<T> callbacks = this.callbacksByChannel.get(channelName);
            if (callbacks != null) {
                if (callbacks.remove(callback)) {
                    if (callbacks.size() == 0) {
                        this.callbacksByChannel.remove(channelName);
                    }

                    return true;
                }
            }

            // Determine if any wildcards exist
            if (this.wildcardingEnabled) {
                this.wildcardingEnabled = false;
                for (final String currChannelName : this.callbacksByChannel.keySet()) {
                    if (hasWildcard(currChannelName)) {
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
     * Fires the specified message to the appropriate (taking into consideration
     * channel) registered listeners.
     *
     * @param message The message to fire
     */
    void fireMessage(final MT message) {
        final String destinationChannel = message.getDestinationTopic();

        //SR 4-18281403991 . Fix Deadlock issues
        //Bug 1228290 - SIA Partner Extension Tycon Rapid Query hangs indefinitely on check-in
        Set<T> globalListeners;
        Set<T> channelListeners;

        managerLock.readLock().lock();
        try {
            globalListeners = this.callbacksByChannel.get(null);
            channelListeners = this.callbacksByChannel.get(destinationChannel);
        } finally {
            managerLock.readLock().unlock();
        }

        // Fire for global listeners (null)
        commonFireMessage(
            globalListeners, message);
        // Fire for channel listeners
        commonFireMessage(
            channelListeners, message);

        // Fire for all wildcarded channels
        if (this.wildcardingEnabled) {
            DxlUtils.iterateWildcards(
                wildcard -> {
                    Set<T> wildCardListeners;
                    managerLock.readLock().lock();
                    try {
                        wildCardListeners = this.callbacksByChannel.get(wildcard);
                    } finally {
                        managerLock.readLock().unlock();
                    }
                    commonFireMessage(wildCardListeners, message);
                },
                destinationChannel
            );
        }
    }

    /**
     * Fires the message to the specified set of callbacks
     *
     * @param callbacks The callbacks to fire the message to
     * @param message   The message to fire
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
     * Method that is to be overridden by derived classes to fire the message to the
     * specified listener.
     *
     * @param listener The target of the message
     * @param message  The message
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
