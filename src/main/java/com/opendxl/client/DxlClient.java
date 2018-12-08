/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client;

import com.opendxl.client.callback.EventCallback;
import com.opendxl.client.callback.RequestCallback;
import com.opendxl.client.callback.ResponseCallback;
import com.opendxl.client.exception.DxlException;
import com.opendxl.client.message.Event;
import com.opendxl.client.message.Message;
import com.opendxl.client.message.Request;
import com.opendxl.client.message.Response;
import com.opendxl.client.ssl.SSLValidationSocketFactory;
import com.opendxl.client.util.Executors;
import com.opendxl.client.util.UuidGenerator;
import org.apache.log4j.Logger;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.security.KeyStore;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Client interface to the Data Exchange Layer (DXL) fabric.
 */
public class DxlClient implements MqttCallback, AutoCloseable {
    /**
     * The logger
     */
    private static Logger logger = Logger.getLogger(DxlClient.class);

    ////////////////////////////////////////////////////////////////////////////
    // Common Properties
    ////////////////////////////////////////////////////////////////////////////

    /**
     * The default "reply-to" prefix. This is typically used for setting up response
     * channels for requests, etc.
     */
    @SuppressWarnings("FieldCanBeLocal")
    private static String replyToPrefix = "/mcafee/client/";

    /**
     * The request callbacks manager
     */
    private final CallbackManager.RequestCallbackManager requestCallbacks =
        new CallbackManager.RequestCallbackManager();

    /**
     * The response callbacks manager
     */
    private final CallbackManager.ResponseCallbackManager responseCallbacks =
        new CallbackManager.ResponseCallbackManager();

    /**
     * The event callbacks manager
     */
    private final CallbackManager.EventCallbackManager eventCallbacks =
        new CallbackManager.EventCallbackManager();

    /**
     * The request manager (manages synchronous and asynchronous request callbacks,
     * notifications, etc.).
     */
    private RequestManager requestManager;

    /**
     * The service manager
     */
    private ServiceManager serviceManager;

    /**
     * The client configuration
     */
    private DxlClientConfig config;

    private SSLSocketFactory socketFactory;

    /**
     * Whether the client has been initialized
     */
    private boolean init = false;

    /**
     * The current broker the client is connected to
     */
    private Broker currentBroker = null;

    /**
     * The current list of subscriptions
     */
    private final Set<String> subscriptions = new HashSet<>();

    /**
     * The number of times to retry during connect, default -1 (infinite)
     */
    private int connectRetries =
        Integer.parseInt(System.getProperty(Constants.SYSPROP_CONNECT_RETRIES, "-1"));

    /**
     * The reconnect delay (in ms), default 10ms
     */
    private int reconnectDelay =
        Integer.parseInt(System.getProperty(Constants.SYSPROP_RECONNECT_DELAY, "1000"));

    /**
     * The reconnect back off multiplier, defaults to 2
     */
    private int backOffMultiplier =
        Integer.parseInt(System.getProperty(Constants.SYSPROP_RECONNECT_BACK_OFF_MULTIPLIER, "2"));

    /**
     * The maximum reconnect delay, default is 1 minute
     */
    private long reconnectDelayMax =
        Long.parseLong(System.getProperty(Constants.SYSPROP_MAX_RECONNECT_DELAY, Long.toString(60 * 1000)));

    /**
     * The reconnect delay random, defaults to 25%
     */
    private float delayRandom =
        Float.parseFloat(System.getProperty(Constants.SYSPROP_RECONNECT_DELAY_RANDOM, "0.25f"));

    /**
     * The default wait time for a synchronous request, defaults to 1 hour
     */
    private long defaultWait =
        Long.parseLong(System.getProperty(Constants.SYSPROP_DEFAULT_WAIT, Long.toString(60 * 60 * 1000)));

    /**
     * The default query timeout (for broker query request
     */
    public long defaultQueryTimeout =
        Long.parseLong(System.getProperty(Constants.SYSPROP_DEFAULT_QUERY_TIMEOUT, Long.toString(10 * 1000)));

    /**
     * The disconnect strategy
     */
    private DisconnectedStrategy disconnectStrategy = new ReconnectDisconnectedStrategy();

    /**
     * The prefix for the message pool thread names
     */
    private String messagePoolPrefix;

    ////////////////////////////////////////////////////////////////////////////
    // MQTT Specific Properties
    ////////////////////////////////////////////////////////////////////////////

    /**
     * The system property for the connect timeout
     */
    private static String syspropMqttConnectTimeout = "dxlClient.mqtt.connectTimeout";

    /**
     * The system property for the connect timeout
     */
    private static String syspropMqttDisconnectTimeout = "dxlClient.mqtt.disconnectTimeout";

    /**
     * The system property for specifying the time to wait for an operation to complete
     */
    private static String syspropMqttTimeToWait = "dxlClient.mqtt.timeToWait";

    /**
     * The reply-to channel
     */
    private String replyToChannel;

    /**
     * Time to wait for an operation to complete
     */
    private long timeToWait =
        Long.parseLong(
            System.getProperty(syspropMqttTimeToWait, Long.toString(120 * 1000)));

    /**
     * Connect timeout
     */
    private int connectTimeout =
        Integer.parseInt(
            System.getProperty(syspropMqttConnectTimeout, Integer.toString(30)));

    /**
     * Connect timeout
     */
    private int disconnectTimeout =
        Integer.parseInt(
            System.getProperty(syspropMqttDisconnectTimeout, Integer.toString(60)));

    /**
     * The underlying MQTT client instance
     */
    private MqttClient client = null;

    /**
     * Thread to handle received messages
     */
    private ExecutorService messageExecutor;

    /**
     * The thread interrupt flag
     */
    private AtomicBoolean interrupt = new AtomicBoolean(false);
    /**
     * The lock for the connect thread
     */
    private Lock connectWaitLock = new ReentrantLock();
    /**
     * The condition associated with the connect thread lock
     */
    private Condition connectWaitCondition = this.connectWaitLock.newCondition();

    /**
     * The lock for trying to connect
     */
    private ReentrantLock connectingLock = new ReentrantLock();
    /**
     * The connecting lock condition
     */
    private Condition connectingLockCondition = connectingLock.newCondition();
    /**
     * boolean indicating if the client is attempting to connect or not
     */
    private boolean attemptingToConnect = false;

    /**
     * Whether the client should infinitely retry to reconnect when it gets disconnected
     */
    private boolean infiniteReconnect = true;

    /**
     * Returns the unique identifier of the client instance
     *
     * @return The unique identifier of the client instance
     */
    public String getUniqueId() {
        return this.config.getUniqueId();
    }

    /**
     * Checks whether the client has been initialized. If it has not been initialized
     * an exception will be thrown.
     *
     * @throws DxlException Thrown if the client has not been initialized
     */
    private void checkInitialized() throws DxlException {
        if (!this.init) {
            throw new DxlException("The client has not been initialized.");
        }
    }

    /**
     * Returns if the client is connected
     *
     * @return true if the client is connected, otherwise false
     */
    public boolean isConnected() {
        return (this.init && this.client != null && this.client.isConnected());
    }

    /**
     * Initializes the state of the client. This should be called via a factory method
     * (bean, etc.) used to create concrete instances of a particular client type.
     */
    private synchronized void init() throws DxlException {
        if (!this.init) {
            doInit();

            this.init = true;
        }
    }

    /**
     * Sets the number of retries to perform when connecting. A value of {@code -1}
     * indicates retry forever.
     *
     * @param retries The number of retries. A value of {@code -1} indicates retry
     *                forever.
     */
    public void setConnectRetries(final int retries) {
        this.connectRetries = retries;
    }

    /**
     * Sets whether the client should infinitely retry to reconnect when it gets disconnected
     *
     * @param infiniteReconnect Whether the client should infinitely retry to reconnect when
     *                          it gets disconnected
     */
    public void setInfiniteReconnectRetries(boolean infiniteReconnect) {
        this.infiniteReconnect = infiniteReconnect;
    }

    /**
     * The amount of time (in ms) for the first connect retry, defaults to {@code 1000}
     *
     * @param delay The amount of time (in ms) for the first connect retry,
     *              defaults to {@code 1000}
     */
    public void setReconnectDelay(final int delay) {
        this.reconnectDelay = delay;
    }

    /**
     * The maximum reconnect delay time, defaults to {@code 60000} (one minute)
     *
     * @param delayMax The maximum reconnect delay time, defaults to {@code 60000}
     *                 (one minute)
     */
    public synchronized void setReconnectDelayMax(final long delayMax) {
        this.reconnectDelayMax = delayMax;
    }

    /**
     * Returns the maximum reconnect delay time, defaults to {@code 60000} (one minute)
     *
     * @return The maximum reconnect delay time, defaults to {@code 60000} (one minute)
     */
    private synchronized long getReconnectDelayMax() {
        return this.reconnectDelayMax;
    }

    /**
     * The exponential reconnect back off multiplier, defaults to {@code 2}
     *
     * @param multiplier The exponential reconnect back off multiplier,
     *                   defaults to {@code 2}
     */
    public void setReconnectBackOffMultiplier(final int multiplier) {
        this.backOffMultiplier = multiplier;
    }

    /**
     * Sets a randomness delay percentage (between {@code 0.0} and {@code 1.0}). When
     * calculating the reconnect delay, this percentage indicates how much randomness there
     * should be in the current delay. For example, if the current delay is 100ms, a value of
     * .25 would mean that the actual delay would be between 100ms and 125ms. The default value
     * is {@code 0.25}
     *
     * @param percent The randomness delay percentage (between {@code 0.0} and
     *                {@code 1.0}
     */
    public void setReconnectDelayRandom(float percent) {
        if (percent < 0.0f || percent > 1.0f) {
            throw new IllegalArgumentException(
                "Percent must be between 0.0 and 1.0");
        }

        this.delayRandom = percent;
    }

    /**
     * Destroys the client instance
     */
    public final void close() throws Exception {
        // Stop any pending connect attempts
        this.interrupt.set(true);

        synchronized (this) {
            if (this.init) {
                doDisconnectQuietly();

                try {
                    this.serviceManager.close();
                } catch (final Exception ex) {
                    logger.error("Error during close of service manager", ex);
                }

                doClose();
                this.init = false;
            }
        }
    }

    /**
     * Connects to the DXL fabric
     *
     * @throws DxlException If a DXL exception occurs
     */
    public final void connect() throws DxlException {
        connect(false);
    }

    /**
     * Connects to the DXL fabric
     *
     * @param reconnect whether this is a reconnect or not(retry counts are ignored on a reconnect)
     * @throws DxlException If a DXL exception occurs
     */
    private void connect(boolean reconnect) throws DxlException {
        this.connectingLock.lock();
        try {
            //check if already attempting to connect
            if (this.attemptingToConnect) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Rejecting attempt to connect because a connect attempt is already occurring.");
                }
                return;
            }

            if (logger.isDebugEnabled()) {
                logger.debug("Attempting to connect.");
            }

            //mark attempting to connecting as true
            this.attemptingToConnect = true;
            this.connectingLockCondition.signalAll();
        } finally {
            this.connectingLock.unlock();
        }

        try {
            try {
                resetClient(this.client.getServerURI());
            } catch (Exception ex) {
                throw new DxlException("Unable to reset client", ex);
            }

            synchronized (this) {
                checkInitialized();

                // Reset the interrupt flag
                this.interrupt.set(false);

                if (isConnected()) {
                    throw new DxlException("Already connected");
                }

                DxlClientConfig config = getConfig();

                // Get the sorted and cleaned map of brokers.
                // Note, the entries in this map are clones.
                final Map<String, Broker> brokers = config.getSortedBrokerMap();
                if (brokers.isEmpty()) {
                    throw new DxlException("No broker defined");
                }

                int retries = this.connectRetries;
                long retryDelay = this.reconnectDelay;
                boolean firstAttempt = true;
                Exception latestEx = null;

                while (!this.interrupt.get()
                    && ((reconnect && this.infiniteReconnect) || this.connectRetries == -1 || retries >= 0)) {
                    if (!firstAttempt) {
                        // Determine retry delay
                        final long reconnectDelayMax = getReconnectDelayMax();
                        retryDelay = (retryDelay > reconnectDelayMax ? reconnectDelayMax : retryDelay);
                        // Apply random after max (so we still have randomness, may exceed maximum)
                        retryDelay += ((this.delayRandom * retryDelay) * Math.random());

                        logger.error("Retrying connect in " + retryDelay + " ms: " + latestEx.getMessage());

                        // Wait...
                        this.connectWaitLock.lock();
                        try {
                            this.connectWaitCondition.await(retryDelay, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException ex) {
                            // Set the interrupt flag
                            this.interrupt.set(true);
                            break;
                        } catch (Exception ex) {
                            logger.error("Error waiting to attempt retry.");
                        } finally {
                            this.connectWaitLock.unlock();
                        }

                        // Update retry delay
                        retryDelay *= this.backOffMultiplier;
                    }

                    try {
                        doConnect(brokers);

                        // Restore connections
                        synchronized (this.subscriptions) {
                            // Get the reply channel
                            final String replyChannel = getReplyToChannel();
                            if (replyChannel != null) {
                                this.subscriptions.add(replyChannel);
                            }

                            for (final String sub : this.subscriptions) {
                                subscribe(sub);
                            }
                        }

                        // Start service registration threads
                        this.serviceManager.onConnect();
                        break;
                    } catch (Exception ex) {
                        // Track latest exception
                        latestEx = ex;
                    }

                    firstAttempt = false;
                    retries--;
                }

                if (!isConnected()) {
                    // Include latest exception
                    throw new DxlException("Error during connect", latestEx);
                }
            }
        } finally {
            //mark attempting to connect as false
            this.connectingLock.lock();
            try {
                this.attemptingToConnect = false;
                this.connectingLockCondition.signalAll();
                if (logger.isDebugEnabled()) {
                    logger.debug("Stopped attempting to connect. ");
                }
            } finally {
                this.connectingLock.unlock();
            }
        }
    }

    /**
     * Disconnects from the DXL fabric
     *
     * @throws DxlException If a DXL exception occurs
     */
    public final void disconnect() throws DxlException {
        checkInitialized();

        // Set the interrupt flag
        this.interrupt.set(true);

        // Signal the connect wait condition
        this.connectWaitLock.lock();
        try {
            this.connectWaitCondition.signalAll();
        } finally {
            this.connectWaitLock.unlock();
        }

        commonDisconnect();
    }

    private synchronized void commonDisconnect() throws DxlException {
        // Stop service registration threads
        this.serviceManager.onDisconnect();
        // Actually disconnect the MQTT client
        doDisconnect();
    }

    /**
     * Disconnects from the DXL fabric unconditionally.
     * A warning will still be logged.
     */
    public final void disconnectQuietly() {
        try {
            disconnect();
        } catch (DxlException ex) {
            logger.warn("Failed attempt to disconnect", ex);
        }
    }

    /**
     * Attempts to reconnect to the server. When performing a reconnect, all existing
     * subscriptions are restored.
     *
     * @throws DxlException If a DXL exception occurs
     */
    public final void reconnect() throws DxlException {
        disconnectQuietly();

        this.connectingLock.lock();
        try {
            while (this.attemptingToConnect) {
                try {
                    this.connectingLockCondition.await();
                } catch (Exception ex) {
                    logger.error("Error waiting for attempting to connect to finish", ex);
                }
            }
        } finally {
            this.connectingLock.unlock();
        }

        connect(true);
    }

    /**
     * Returns the current broker ID the client is connected to
     *
     * @return The current broker ID the client is connected to or null if not connected
     */
    public String getCurrentBrokerId() {
        return (isConnected() && this.currentBroker != null ? this.currentBroker.getUniqueId() : null);
    }

    /**
     * Returns the current Broker object the client is connected to
     *
     * @return The current Broker object the client is connected to or null if not connected
     */
    public Broker getCurrentBroker() {
        return (isConnected() ? this.currentBroker : null);
    }

    /**
     * Sets the current broker
     *
     * @param broker The current broker
     */
    private synchronized void setCurrentBroker(final Broker broker) {
        this.currentBroker = broker;
    }

    /**
     * Resets the current broker
     */
    private void resetCurrentBroker() {
        setCurrentBroker(null);
    }

    /**
     * Subscribes to a channel on the DXL fabric
     *
     * @param channel The channel name
     * @throws DxlException If a DXL exception occurs
     */
    public final void subscribe(final String channel) throws DxlException {
        synchronized (this.subscriptions) {
            this.subscriptions.add(channel);
        }

        if (isConnected()) {
            doSubscribe(channel);
        }
    }

    /**
     * Unsubscribes from a channel on the DXL fabric
     *
     * @param channel The channel name
     * @throws DxlException If a DXL exception occurs
     */
    public final void unsubscribe(final String channel) throws DxlException {
        if (isConnected()) {
            doUnsubscribe(channel);
        }

        synchronized (this.subscriptions) {
            this.subscriptions.remove(channel);
        }
    }

    /**
     * Returns the set of current subscriptions
     *
     * @return The set of current subscriptions
     */
    public Set<String> getSubscriptions() {
        synchronized (this.subscriptions) {
            return this.subscriptions;
        }
    }

    /**
     * Returns the size of the current request queue
     *
     * @return The size of the current request queue
     */
    @Deprecated
    public int getCurrentRequestQueueSize() {
        return 0;
    }

    /**
     * Performs a synchronous request with the default timeout via the DXL fabric
     *
     * @param request The request
     * @return The response
     * @throws DxlException If an error occurs, or the operation times out
     * @see com.opendxl.client.exception.WaitTimeoutException
     */
    public Response syncRequest(final Request request) throws DxlException {
        return syncRequest(request, this.defaultWait);
    }

    /**
     * Performs a synchronous request with the specified timeout via the DXL fabric
     *
     * @param request    The request
     * @param waitMillis Wait timeout in milliseconds
     * @return The response
     * @throws DxlException If an error occurs, or the operation times out
     * @see com.opendxl.client.exception.WaitTimeoutException
     */
    public Response syncRequest(final Request request, final long waitMillis) throws DxlException {
        checkInitialized();
        request.setSourceClientId(getUniqueId());
        return this.requestManager.syncRequest(request, waitMillis);
    }

    /**
     * Performs an asynchronous request via the DXL fabric
     *
     * @param request    The request
     * @param callback   The callback to be invoked when the response is received
     * @param waitMillis The amount of time to wait for a response before removing the callback
     * @throws DxlException If a DXL exception occurs
     */
    public void asyncRequest(final Request request, final ResponseCallback callback, final long waitMillis)
        throws DxlException {
        checkInitialized();
        request.setSourceClientId(getUniqueId());
        this.requestManager.asyncRequest(request, callback, waitMillis);
    }

    /**
     * Performs an asynchronous request via the DXL fabric
     *
     * @param request  The request
     * @param callback The callback to be invoked when the response is received
     * @throws DxlException If a DXL exception occurs
     */
    public void asyncRequest(final Request request, final ResponseCallback callback) throws DxlException {
        asyncRequest(request, callback, this.defaultWait);
    }

    /**
     * Performs an asynchronous request via the DXL fabric
     *
     * @param request The request
     * @return The request message identifier
     * @throws DxlException If a DXL exception occurs
     */
    public String asyncRequest(final Request request) throws DxlException {
        asyncRequest(request, null);
        return request.getMessageId();
    }

    /**
     * Sends the specified event to registered listeners via the DXL fabric
     *
     * @param event The event to send
     * @throws DxlException If a DXL exception occurs
     */
    public void sendEvent(final Event event) throws DxlException {
        checkInitialized();
        event.setSourceClientId(getUniqueId());
        doSendEvent(event, event.getDestinationChannel(), packMessage(event));
    }

    /**
     * Sends the specified response back to the requester via the DXL fabric
     *
     * @param response The response to send
     * @throws DxlException If a DXL exception occurs
     */
    public void sendResponse(final Response response) throws DxlException {
        checkInitialized();
        response.setSourceClientId(getUniqueId());
        doSendResponse(response, response.getDestinationChannel(), packMessage(response));
    }

    /**
     * Adds a {@link RequestCallback} listener to the client. This listener will be
     * notified of all requests received by the client instance on the specified
     * channel. A {@code null} channel indicates that the callback should receive
     * requests from all channels (no channel filtering).
     *
     * @param channel  The channel that the requests are received on. A {@code null}
     *                 channel indicates that the callback should receive requests from all
     *                 channels (no channel filtering).
     * @param callback The request callback
     */
    public void addRequestCallback(final String channel, final RequestCallback callback) {
        this.requestCallbacks.addCallback(channel, callback);
    }

    /**
     * Removes the {@link RequestCallback} listener from the client. This method should
     * be called with the same arguments as when the callback was originally registered.
     *
     * @param channel  The channel to remove the callback from (or {@code null}).
     * @param callback The request callback
     * @see #addRequestCallback(String, RequestCallback)
     */
    public void removeRequestCallback(final String channel, final RequestCallback callback) {
        this.requestCallbacks.removeCallback(channel, callback);
    }

    /**
     * Adds a {@link ResponseCallback} listener to the client. This listener will be
     * notified of all responses received by the client instance on the specified
     * channel. A {@code null} channel indicates that the callback should receive
     * responses from all channels (no channel filtering).
     *
     * @param channel  The channel that the responses are received on. A {@code null}
     *                 channel indicates that the callback should receive responses from all
     *                 channels (no channel filtering).
     * @param callback The response callback
     */
    public void addResponseCallback(final String channel, final ResponseCallback callback) {
        this.responseCallbacks.addCallback(channel, callback);
    }

    /**
     * Removes the {@link ResponseCallback} listener from the client. This method should
     * be called with the same arguments as when the callback was originally registered.
     *
     * @param channel  The channel to remove the callback from (or {@code null}).
     * @param callback The response callback
     * @see #addResponseCallback(String, ResponseCallback)
     */
    public void removeResponseCallback(final String channel, final ResponseCallback callback) {
        this.responseCallbacks.removeCallback(channel, callback);
    }

    /**
     * Adds an {@link EventCallback} listener to the client. This listener will be
     * notified of all events received by the client instance on the specified
     * channel. A {@code null} channel indicates that the callback should receive
     * events from all channels (no channel filtering).
     *
     * @param channel  The channel that the events are received on. A {@code null}
     *                 channel indicates that the callback should receive events from all
     *                 channels (no channel filtering).
     * @param callback The event callback
     */
    public void addEventCallback(String channel, EventCallback callback) {
        this.eventCallbacks.addCallback(channel, callback);
    }

    /**
     * Removes the {@link EventCallback} listener from the client. This method should
     * be called with the same arguments as when the callback was originally registered.
     *
     * @param channel  The channel to remove the callback from (or {@code null}).
     * @param callback The event callback
     * @see #addEventCallback(String, EventCallback)
     */
    public void removeEventCallback(final String channel, final EventCallback callback) {
        this.eventCallbacks.removeCallback(channel, callback);
    }

    /**
     * Registers a service with the DXL fabric. See {@link ServiceRegistrationInfo}
     * <p>
     * NOTE: This method requires that the client is currently connected to a broker.
     * </p>
     *
     * @param service The service registration info
     * @param timeout The amount of time (in ms) to wait for the service to register with the broker. If this
     *                timeout is exceeded an exception is thrown. However, it is important to note that the client
     *                will continue to perform the registration in the background.
     * @throws DxlException If a DXL exception occurs
     */
    public void registerServiceSync(final ServiceRegistrationInfo service, final long timeout)
        throws DxlException {
        if (!isConnected()) {
            throw new DxlException("The client is not currently connected");
        }

        this.serviceManager.addService(service);
        service.waitForRegistration(timeout);
    }

    /**
     * Registers the service asynchronously (does not wait for it to be registered with the broker).
     * <p>
     * This method does not require the client to be currently connected to a broker.
     * </p>
     *
     * @param service The service registration info
     * @throws DxlException If a DXL exception occurs
     */
    public void registerServiceAsync(ServiceRegistrationInfo service) throws DxlException {
        this.serviceManager.addService(service);
    }

    /**
     * Unregisters a service from the DXL fabric.
     * <p>
     * NOTE: This method requires that the client is currently connected to a broker.
     * </p>
     *
     * @param service The service registration info
     * @param timeout The amount of time (in ms) to wait for the service to unregister with the broker. If this
     *                timeout is exceeded an exception is thrown. However, it is important to note that the client
     *                will continue to perform the unregistration in the background.
     * @throws DxlException If a DXL exception occurs
     */
    public void unregisterServiceSync(final ServiceRegistrationInfo service, final long timeout)
        throws DxlException {
        if (!isConnected()) {
            throw new DxlException("The client is not currently connected");
        }

        if (service == null) {
            throw new IllegalArgumentException("Undefined service object");
        }

        this.serviceManager.removeService(service.getServiceGuid());
        service.waitForUnregistration(timeout);
    }

    /**
     * Unregisters the service asynchronously (does not wait for it to be registered with the broker).
     * <p>
     * This method does not require the client to be currently connected to a broker.
     * </p>
     *
     * @param service The service registration info
     * @throws DxlException If a DXL exception occurs
     */
    public void unregisterServiceAsync(ServiceRegistrationInfo service) throws DxlException {
        this.serviceManager.removeService(service.getServiceGuid());
    }

    /**
     * Unregisters a service from the DXL fabric.
     *
     * @param serviceGuid The unique ID of the service to unregister
     * @throws DxlException If a DXL exception occurs
     */
    public void unregisterServiceAsync(final String serviceGuid) throws DxlException {
        this.serviceManager.removeService(UuidGenerator.normalize(serviceGuid));
    }

    /**
     * Fires the specified {@link Event} to {@link EventCallback} listeners currently
     * registered with the client.
     *
     * @param event The {@link Event} to fire.
     * @see #addEventCallback(String, com.opendxl.client.callback.EventCallback)
     */
    private void fireEvent(final Event event) {
        this.eventCallbacks.fireMessage(event);
    }

    /**
     * Fires the specified {@link Response} to {@link ResponseCallback} listeners currently
     * registered with the client.
     *
     * @param response The {@link Response} to fire.
     * @see #addResponseCallback(String, com.opendxl.client.callback.ResponseCallback)
     */
    private void fireResponse(final Response response) {
        this.responseCallbacks.fireMessage(response);
    }

    /**
     * Fires the specified {@link Request} to {@link RequestCallback} listeners currently
     * registered with the client.
     *
     * @param request The {@link Request} to fire.
     * @see #addRequestCallback(String, com.opendxl.client.callback.RequestCallback)
     */
    private void fireRequest(final Request request) {
        this.requestCallbacks.fireMessage(request);
    }

    /**
     * Invoked when the client has been disconnected by the broker.
     * To be invoked by concrete implementation.
     */
    private void handleDisconnected() {
        //Bug Fix : https://bugzilla.corp.nai.org/bugzilla/show_bug.cgi?id=1230697
        disconnectQuietly();

        // Check for disconnected strategy
        if (this.disconnectStrategy != null) {
            // Check to see if the disconnected strategy has been disabled via the system property
            // Mostly for testing, performance, etc.
            if (!(Boolean.parseBoolean(
                System.getProperty(Constants.SYSPROP_DISABLE_DISCONNECTED_STRATEGY, "false")))) {
                this.disconnectStrategy.onDisconnect(this);
            }
        }
    }

    /**
     * Sets the strategy to use if the client becomes unexpectedly disconnected from the
     * broker. By default the {@link ReconnectDisconnectedStrategy} is used.
     *
     * @param strategy The strategy to use if the client becomes unexpectedly disconnected
     *                 from the broker. By default the {@link ReconnectDisconnectedStrategy} is used.
     */
    public void setDisconnectedStrategy(final DisconnectedStrategy strategy) {
        this.disconnectStrategy = strategy;
    }

    /**
     * Processes an incoming message. The bytes from the message are converted into the appropriate
     * message type instance (request, response, event, etc.) and then the corresponding registered
     * message callbacks are notified.
     *
     * @param channel The channel that the message arrived on
     * @param bytes   The message received from the channel (as bytes)
     */
    private void handleMessage(final String channel, final byte[] bytes) throws IOException {
        final Message message = Message.fromBytes(bytes);
        // Set the channel that the message was delivered on
        message.setDestinationChannel(channel);
        switch (message.getMessageType()) {
            case Message.MESSAGE_TYPE_EVENT:
                this.fireEvent((Event) message);
                break;
            case Message.MESSAGE_TYPE_REQUEST:
                this.fireRequest((Request) message);
                break;
            case Message.MESSAGE_TYPE_RESPONSE:
            case Message.MESSAGE_TYPE_ERROR:
                this.fireResponse((Response) message);
                break;
            default:
                throw new IOException("Unknown message type");
        }
    }

    /**
     * Sends the specified request to the DXL fabric
     *
     * @param request The request to send to the DXL fabric
     */
    void sendRequest(final Request request) throws DxlException {
        setReplyToForMessage(request);
        doSendRequest(request, request.getDestinationChannel(), packMessage(request));
    }

    /**
     * Packs the specified message (converts the message to a byte array) for transmitting
     * over the DXL fabric.
     *
     * @param message The message to pack
     * @return The packed message (as bytes)
     */
    private byte[] packMessage(final Message message) throws DxlException {
        byte[] bytes;
        try {
            bytes = message.toBytes();
        } catch (final IOException ex) {
            throw new DxlException("Error packing request", ex);
        }

        return bytes;
    }

    ////////////////////////////////////////////////////////////////////////////
    // MQTT Specific Accessor
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Sets the reply-to channel
     *
     * @param channel The reply-to channel
     */
    void setReplyToChannel(final String channel) {
        this.replyToChannel = channel;
    }

    /**
     * Returns the name of the "reply-to" channel to use for communicating back to this client
     * (responses to requests).
     *
     * @return The name of the "reply-to" channel to use for communicating back to this client
     * (responses to requests).
     */
    private String getReplyToChannel() {
        return this.replyToChannel;
    }

    /**
     * Sets the time to wait for an operation to complete
     *
     * @param timeToWait The time to wait for an operation to complete (in milliseconds)
     */
    public void setTimeToWait(final long timeToWait) {
        this.timeToWait = timeToWait;
    }

    /**
     * Sets the connection timeout
     *
     * @param timeout The connection timeout
     */
    public void setConnectionTimeout(final int timeout) {
        this.connectTimeout = timeout;
    }

    /**
     * Returns the current DXL client configuration
     *
     * @return The current DXL client configuration
     */
    public DxlClientConfig getConfig() {
        return this.config;
    }

    /**
     * Return whether the invoking thread as the "incoming message" thread
     *
     * @return Whether the invoking thread as the "incoming message" thread
     */
    boolean isIncomingMessageThread() {
        return Thread.currentThread().getName().startsWith(this.messagePoolPrefix);
    }

    ////////////////////////////////////////////////////////////////////////////
    // Implements MqttCallback
    ////////////////////////////////////////////////////////////////////////////

    /**
     * {@inheritDoc}
     */
    @Override
    public void connectionLost(final Throwable cause) {
        logger.error("connectionLost: " + cause.getMessage()
            + ", disconnection strategy will be invoked.", cause);

        // Allow for disconnection strategy
        handleDisconnected();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deliveryComplete(final IMqttDeliveryToken token) {
        if (logger.isDebugEnabled()) {
            logger.debug("deliveryComplete: " + token.getMessageId());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void messageArrived(final String channel, final MqttMessage message) {
        if (logger.isDebugEnabled()) {
            logger.debug("messageArrived: " + channel);
        }

        // Handle any received messages on a separate thread.
        // If you invoke any MQTT client-related methods (subscribe/un-subscribe, etc.)
        // during a handled message, deadlock will occur. The received messages
        // are on the "MQTT Call" thread.
        this.messageExecutor.execute(
            () -> {
                try {
                    handleMessage(channel, message.getPayload());
                } catch (Throwable t) {
                    logger.error("Error during message handling", t);
                }
            }
        );
    }

    ////////////////////////////////////////////////////////////////////////////
    // MQTT Specific Implementation
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Creates a new MQTT client (replaces existing)
     *
     * @param serverUri The server URI
     * @throws MqttException An MQTT exception
     */
    private void resetClient(final String serverUri) throws MqttException {
        this.client = new MqttClient(serverUri, this.getUniqueId(), new MemoryPersistence());

        // This is a global operation timeout
        this.client.setTimeToWait(this.timeToWait);
    }

    /**
     * Performs client initialization
     */
    private synchronized void doInit() throws DxlException {
        DxlClientConfig config = getConfig();

        if (config == null) {
            throw new DxlException("No client configuration");
        }
        if (config.getBrokerList() == null || config.getBrokerList().isEmpty()) {
            throw new DxlException("No broker defined");
        }

        final KeyStore ks = config.getKeyStore();

        try {
            this.socketFactory = SSLValidationSocketFactory.newInstance(ks, DxlClientConfig.KS_PASS);

            // Use the first broker in the list for initialization. This will be overwritten before connect.
            Broker broker = config.getBrokerList().get(0);
            resetClient(broker.toString());

            // The reply-to channel name
            this.replyToChannel = replyToPrefix + this.getUniqueId();

            // Prefix for the message pool
            this.messagePoolPrefix = "DxlMessagePool-" + this.getUniqueId();

            //
            // Thread to handle messages received. Ensure the thread is a
            // daemon thread.
            //
            this.messageExecutor =
                Executors.createDaemonExecutor(
                    config.getIncomingMessageThreadPoolSize(),
                    config.getIncomingMessageQueueSize(),
                    this.messagePoolPrefix);
        } catch (final Exception ex) {
            throw new DxlException("Error creating client", ex);
        }
    }

    /**
     * Destroys the client instance
     */
    private synchronized void doClose()
        throws DxlException {
        if (this.client != null) {
            try {
                try {
                    this.client.close();
                } finally {
                    try {
                        if (this.messageExecutor != null) {
                            this.messageExecutor.shutdown();
                        }
                    } finally {
                        this.requestManager.close();
                    }
                }
            } catch (final Exception ex) {
                throw new DxlException("Error during close", ex);
            }
        }
    }

    /**
     * Connects to the DXL fabric
     */
    private synchronized void doConnect(final Map<String, Broker> brokers)
        throws DxlException {
        Exception latestEx = null;

        try {
            doDisconnectQuietly();

            this.client.setCallback(this);

            final MqttConnectOptions connectOps = new MqttConnectOptions();

            connectOps.setCleanSession(true);
            connectOps.setKeepAliveInterval(getConfig().getKeepAliveInterval());
            connectOps.setConnectionTimeout(this.connectTimeout);

            // Set socket factory if applicable
            connectOps.setSocketFactory(socketFactory);

            for (Map.Entry<String, Broker> entry : brokers.entrySet()) {
                if (this.interrupt.get()) {
                    break;
                }

                String[] brokerURIs = new String[1];
                brokerURIs[0] = entry.getKey();

                String brokerInfoString = entry.getValue().getUniqueId() + " (" + entry.getKey() + ")";

                logger.info("Trying to connect to broker: " + brokerInfoString);

                try {
                    connectOps.setServerURIs(brokerURIs);
                    this.client.connect(connectOps);
                    setCurrentBroker(entry.getValue());

                    logger.info("Client connected to broker: " + brokerInfoString);

                    break;
                } catch (Exception ex) {
                    logger.error("Failed to connect to broker: " + brokerInfoString + ": " + ex.getMessage());
                    latestEx = ex;
                }
            }
        } catch (final Exception ex) {
            throw new DxlException(ex);
        }

        if (!isConnected()) {
            if (latestEx != null) {
                throw new DxlException(latestEx);
            }
        }
    }

    /**
     * Disconnects from the DXL fabric
     */
    private synchronized void doDisconnect()
        throws DxlException {
        try {
            if (this.client.isConnected()) {
                final DisconnectThread dt = new DisconnectThread(this.client);
                dt.start();

                // Wait for disconnect
                dt.join(this.disconnectTimeout * 1000);

                if (!dt.isSuccess()) {
                    logger.error("Disconnect operation timed out! Creating new MQTT client.");
                    // Create a new MQTT client
                    resetClient(this.client.getServerURI());
                }
            }
        } catch (final Exception ex) {
            throw new DxlException("Error during disconnect", ex);
        } finally {
            resetCurrentBroker();
        }
    }

    /**
     * Disconnects from the DXL fabric unconditionally.
     * A warning will still be logged.
     */
    private synchronized void doDisconnectQuietly() {
        if (isConnected()) {
            try {
                doDisconnect();
            } catch (DxlException ex) {
                logger.warn("Failed attempt to disconnect", ex);
            }
        }
    }

    /**
     * Subscribes to a channel on the DXL fabric
     *
     * @param channel The channel name
     */
    private void doSubscribe(final String channel)
        throws DxlException {
        try {
            this.client.subscribe(channel);
        } catch (final Exception ex) {
            throw new DxlException("Error during subscribe", ex);
        }
    }

    /**
     * Unsubscribes from a channel on the DXL fabric
     *
     * @param channel The channel name
     */
    private void doUnsubscribe(final String channel)
        throws DxlException {
        try {
            this.client.unsubscribe(channel);
        } catch (final Exception ex) {
            throw new DxlException("Error during subscribe", ex);
        }
    }

    /**
     * Publishes the specified message
     *
     * @param channel The channel to publish on
     * @param bytes   The message content
     * @param qos     The quality of service (QOS)
     */
    private void publishMessage(final String channel, final byte[] bytes, int qos)
        throws DxlException {
        try {
            this.client.publish(channel, bytes, qos, false);
        } catch (final Exception ex) {
            throw new DxlException("Error publishing message", ex);
        }
    }

    /**
     * Sends a request to the DXL fabric
     *
     * @param request The Request
     * @param channel The channel name
     * @param bytes   The request
     */
    private void doSendRequest(final Request request, final String channel, final byte[] bytes)
        throws DxlException {
        publishMessage(channel, bytes, 0 /* Only supports 0 currently */);
    }

    /**
     * Sends a response to the DXL fabric
     *
     * @param response The response
     * @param channel  The channel name
     * @param bytes    The response
     */
    private void doSendResponse(final Response response, final String channel, final byte[] bytes)
        throws DxlException {
        publishMessage(channel, bytes, 0 /* Only supports 0 currently */);
    }

    /**
     * Sends an event to the DXL fabric (to be implemented by concrete classes).
     *
     * @param event   The event
     * @param channel The channel name
     * @param bytes   The event
     */
    private void doSendEvent(final Event event, final String channel, final byte[] bytes)
        throws DxlException {
        publishMessage(channel, bytes, 0 /* Only supports 0 currently */);
    }

    /**
     * The purpose of this method is to allow to specify the appropriate "reply-to"
     * path {@link Request#setReplyToChannel(String)} for the specified {@link Request}.
     *
     * @param request The {@link Request} to set the "reply-to" on
     */
    private void setReplyToForMessage(final Request request)
        throws DxlException {
        request.setReplyToChannel(this.replyToChannel);
    }

    /**
     * Returns the count of async callbacks that are waiting for a response
     *
     * @return The count of async callbacks that are waiting for a response
     */
    int getAsyncCallbackCount() {
        return this.requestManager.getAsyncCallbackCount();
    }

    ////////////////////////////////////////////////////////////////////////////
    // Constructor
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Constructs the client
     *
     * @param config The DXL client configuration
     * @throws DxlException If a DXL exception occurs
     */
    public DxlClient(DxlClientConfig config)
        throws DxlException {
        if (config == null) {
            throw new DxlException("No client configuration specified");
        }
        if (config.getUniqueId() == null || config.getUniqueId().isEmpty()) {
            throw new DxlException("No unique identifier specified");
        }

        this.config = config;

        this.requestManager = new RequestManager(this);
        this.serviceManager = new ServiceManager(this);

        init();
    }

    /**
     * Thread used to disconnect. Allows us to detect disconnect failure. If the disconnect fails,
     * we swap the underlying MQTT client. This was added to resolved deadlocks we were seeing during
     * disconnect.
     */
    private static class DisconnectThread extends Thread {
        /**
         * Whether the disconnect operation succeeded
         */
        private AtomicBoolean succeeded = new AtomicBoolean(false);

        /**
         * The MQTT client to disconnect
         */
        private MqttClient disconnectClient;

        /**
         * Constructs the thread
         *
         * @param client The MQTT client to disconnect
         */
        DisconnectThread(final MqttClient client) {
            super();
            this.disconnectClient = client;
        }

        /**
         * Returns whether the disconnect operation succeeded
         *
         * @return Whether the disconnect operation succeeded
         */
        boolean isSuccess() {
            return this.succeeded.get();
        }

        @Override
        public void run() {
            try {
                this.disconnectClient.disconnect();
                this.succeeded.set(true);
            } catch (Exception ex) {
                logger.error("Failure during disconnect", ex);
            }
        }
    }
}
