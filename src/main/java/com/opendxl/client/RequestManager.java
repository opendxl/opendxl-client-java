/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client;

import com.opendxl.client.callback.ResponseCallback;
import com.opendxl.client.exception.DxlException;
import com.opendxl.client.exception.WaitTimeoutException;
import com.opendxl.client.message.Request;
import com.opendxl.client.message.Response;
import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manager that tracks outstanding requests and notifies the appropriate parties (invoking a response callback,
 * notifying a waiting object, etc.) when a corresponding response is received.
 * <p/>
 * This purpose of this object is to collaborate with an {@link DxlClient} instance.
 * <p/>
 */
class RequestManager implements ResponseCallback {

    /**
     * The logger
     */
    private static Logger logger = Logger.getLogger(RequestManager.class);

    /**
     * The interval at which to check asynchronous callbacks for removal (Defaults to 5 minutes)
     */
    @SuppressWarnings("FieldCanBeLocal")
    private int asyncCallbackCheckInterval =
        Integer.parseInt(System.getProperty(
            Constants.SYSPROP_ASYNC_CALLBACK_CHECK_INTERVAL, Integer.toString(5 * 60 * 1000)));

    /**
     * Map containing {@link ResponseCallback} instances associated with the identifier of the request message that
     * they are waiting for a response to. This map is used for asynchronous requests.
     */
    private Map<String, AsyncResponseCallbackHolder> callbackMap = new ConcurrentHashMap<>();

    /**
     * Lock for request threads waiting for a response (for synchronous request)
     */
    private Lock syncWaitMessageLock = new ReentrantLock();

    /**
     * The condition associated with the request threads waiting for a response (for synchronous request).
     */
    private Condition syncWaitMessageCondition = this.syncWaitMessageLock.newCondition();

    /**
     * The set of identifiers for request messages that are waiting for a response (for synchronous request).
     */
    private Set<String> syncWaitMessageIds = new HashSet<>();

    /**
     * Messages that have been received mapped by the corresponding request message identifier
     * (for synchronous request).
     */
    private Map<String, Response> syncWaitMessageResponses = new HashMap<>();

    /**
     * The client that the request manager is associated with
     */
    private DxlClient client;

    /**
     * The lock used for checking asynchronous callbacks for timeout
     */
    private Lock asyncCallbackCheckLock = new ReentrantLock();

    /**
     * The timer task for checking asynchronous callbacks
     */
    private AsyncResponseTimerTask asyncResponseTimerTask = new AsyncResponseTimerTask();

    /**
     * A timer for performing maintenance tasks
     */
    private static Timer maintenanceTimer = new Timer(true);

    /**
     * Constructor for {@link RequestManager}
     *
     * @param client The client that this request manager is associated with
     */
    RequestManager(final DxlClient client) {
        this.client = client;

        // Register the timer task
        maintenanceTimer.schedule(
            this.asyncResponseTimerTask,
            this.asyncCallbackCheckInterval,
            this.asyncCallbackCheckInterval
        );

        // Register with the client as a response listener (all responses regardless
        // of topic).
        client.addResponseCallback(null, this);
    }

    /**
     * Performs a synchronous request with the default timeout via the DXL fabric
     *
     * @param request The request
     * @return The response
     * @throws DxlException If an error occurs, or the operation times out
     * @see com.opendxl.client.exception.WaitTimeoutException
     */
    public Response syncRequest(final Request request, final long waitMillis) throws DxlException {
        // Don't allow synchronous requests on message handler threads
        if (this.client.isIncomingMessageThread()) {
            throw new DxlException(
                "Synchronous requests may not be invoked while handling an incoming message. "
                    + "The synchronous request must be made on a different thread.");
        }

        Response response = null;
        registerWaitForResponse(request);
        try {
            try {
                this.client.sendRequest(request);
                response = waitForResponse(request, waitMillis);
            } catch (final Exception ex) {
                DxlUtils.throwWrappedException("Error during synchronous request", ex);
            }
        } finally {
            unregisterWaitForResponse(request);
        }

        return response;
    }

    /**
     * Performs an asynchronous request via the DXL fabric
     *
     * @param request The request
     * @param callback The callback to be invoked when the response is received
     * @param waitMillis The amount of time to wait for a response before removing the callback
     */
    void asyncRequest(
        final Request request, final ResponseCallback callback, final long waitMillis) throws DxlException {
        if (callback == null) {
            this.client.sendRequest(request);
        } else {
            registerAsyncCallback(request, callback, waitMillis);

            try {
                this.client.sendRequest(request);
            } catch (final Exception ex) {
                unregisterAsyncCallback(request.getMessageId());
                DxlUtils.throwWrappedException("Error during asynchronous request", ex);
            }
        }
    }

    /**
     * Indicates to the request manager that you are about to wait for the specified request (synchronous response).
     * The registration has to occur prior to actually sending the request message to account for the possibility of
     * the response being received immediately.
     *
     * @param request The request that is about to be waited for.
     * @see DxlClient#syncRequest(com.opendxl.client.message.Request, long)
     */
    private void registerWaitForResponse(final Request request) {
        this.syncWaitMessageLock.lock();
        try {
            this.syncWaitMessageIds.add(request.getMessageId());
        } finally {
            this.syncWaitMessageLock.unlock();
        }
    }

    /**
     * Indicates to the request manager that you no longer want to wait for the specified request
     * (synchronous response). This must be invoked when an error occurs while waiting for the response, or the
     * response was received.
     *
     * @param request The request that should no longer be waited for
     * @see DxlClient#syncRequest(com.opendxl.client.message.Request, long)
     */
    private void unregisterWaitForResponse(final Request request) {
        this.syncWaitMessageLock.lock();
        try {
            this.syncWaitMessageIds.remove(request.getMessageId());
            this.syncWaitMessageResponses.remove(request.getMessageId());
        } finally {
            this.syncWaitMessageLock.unlock();
        }
    }

    /**
     * Waits for a response to the specified request up to the specified wait time in milliseconds.
     *
     * @param request The request for which to wait for the response
     * @param waitMillis The maximum time to wait for the request
     * @return The response
     * @throws InterruptedException If the wait is interrupted
     * @throws WaitTimeoutException If the specified wait time is exceeded
     * @see DxlClient#syncRequest(com.opendxl.client.message.Request, long)
     */
    private Response waitForResponse(final Request request, final long waitMillis)
        throws InterruptedException, WaitTimeoutException {
        final String messageId = request.getMessageId();
        Response response;

        this.syncWaitMessageLock.lock();
        try {
            // Keep track of time when we submitted the request.
            long waitStartMillis = System.currentTimeMillis();

            while ((response = this.syncWaitMessageResponses.remove(messageId)) == null) {
                // We run into this loop every time we receive a response and it wasn't the one we were expecting.
                // Now calculate the remaining time to wait.
                long remainingWaitMillis = waitMillis - (System.currentTimeMillis() - waitStartMillis);

                // If the remaining time to wait has expired or the condition doesn't trigger
                // within the remaining time to wait, throw a wait timeout exception.
                if (remainingWaitMillis <= 0
                    || !this.syncWaitMessageCondition.await(remainingWaitMillis, TimeUnit.MILLISECONDS)) {
                    throw new WaitTimeoutException(
                        "Timeout waiting for response to message: " + messageId);
                }
            }
        } finally {
            this.syncWaitMessageLock.unlock();
        }

        return response;
    }

    /**
     * Registers an asynchronous callback for the specified request
     *
     * @param request The request
     * @param callback The callback to invoke when the response for the request is received.
     * @param waitMillis The amount of time to wait for a response before removing the callback
     * @see DxlClient#asyncRequest(Request, ResponseCallback)
     */
    private void registerAsyncCallback(
        final Request request, final ResponseCallback callback, final long waitMillis) throws DxlException {
        this.callbackMap.put(request.getMessageId(), new AsyncResponseCallbackHolder(callback, waitMillis));
    }

    /**
     * Removes an asynchronous callback for the specified request
     *
     * @param messageId The identifier for the request
     * @see DxlClient#asyncRequest(Request, ResponseCallback)
     */
    private ResponseCallback unregisterAsyncCallback(final String messageId) throws DxlException {
        final AsyncResponseCallbackHolder holder = this.callbackMap.remove(messageId);
        return holder != null ? holder.getCallback() : null;
    }

    /**
     * Returns the count of asynchronous callbacks that are waiting for a response
     *
     * @return The count of asynchronous callbacks that are waiting for a response
     */
    int getAsyncCallbackCount() {
        return this.callbackMap.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onResponse(final Response response) {
        final String requestMessageId = response.getRequestMessageId();

        // Check for synchronous waits
        this.syncWaitMessageLock.lock();
        try {
            if (this.syncWaitMessageIds.remove(requestMessageId)) {
                this.syncWaitMessageResponses.put(requestMessageId, response);
                this.syncWaitMessageCondition.signalAll();
            }
        } finally {
            this.syncWaitMessageLock.unlock();
        }

        // Check for asynchronous callbacks
        final AsyncResponseCallbackHolder holder = this.callbackMap.remove(requestMessageId);
        if (holder != null) {
            holder.getCallback().onResponse(response);
        }
    }

    /**
     * Wrapper around an asynchronous response callback
     */
    private static class AsyncResponseCallbackHolder {

        /**
         * The callback
         */
        private ResponseCallback callback;

        /**
         * When the callback expires
         */
        private long expiration;

        /**
         * Constructs the wrapper
         *
         * @param callback The response callback
         * @param waitTime The amount of time to wait for the response
         */
        AsyncResponseCallbackHolder(final ResponseCallback callback, final long waitTime) {
            this.callback = callback;
            this.expiration = System.currentTimeMillis() + waitTime;
        }

        /**
         * Returns the callback
         *
         * @return The callback
         */
        public ResponseCallback getCallback() {
            return this.callback;
        }

        /**
         * Whether the callback has timed out
         *
         * @return Whether the callback has timed out
         */
        boolean isExpired() {
            return System.currentTimeMillis() >= this.expiration;
        }
    }

    /**
     * Cleans up state related to the request manager
     */
    void close() {
        this.asyncResponseTimerTask.cancel();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void finalize() {
        close();
    }

    /**
     * Timer task to cleanup asynchronous response listeners
     */
    private class AsyncResponseTimerTask extends TimerTask {
        /**
         * {@inheritDoc}
         */
        @Override
        public void run() {
            asyncCallbackCheckLock.lock();
            try {
                // Remove the entry
                callbackMap.entrySet().removeIf(entry -> entry.getValue().isExpired());
            } finally {
                asyncCallbackCheckLock.unlock();
            }
        }
    }
}
