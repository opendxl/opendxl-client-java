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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The {@link DxlClient} class is responsible for all communication with the Data Exchange Layer (DXL) fabric (it can
 * be thought of as the "main" class). All other classes exist to support the functionality provided by the client.
 * <P>
 * The following example demonstrates the configuration of a {@link DxlClient} instance and connecting it to the fabric:
 * </P>
 * <PRE>
 * DxlClientConfig config = DxlClientConfig.createDxlConfigFromFile("dxlclient.config");
 * try (DxlClient client = new DxlClient(config)) {
 *     client.connect();
 *
 *     // do work here
 * }
 * </PRE>
 * <P>
 * <b>NOTE:</b> The preferred way to construct the client is with a <i>try-with-resource</i> statement as shown above.
 * This ensures that resources associated with the client are properly cleaned up when the block is exited.
 * </P>
 * <P>
 * The following classes and packages support the client:
 * </P>
 * <UL>
 * <LI>
 * {@link DxlClientConfig}: This class holds the information necessary to connect a {@link DxlClient} to the DXL
 *      fabric.
 * </LI>
 * <LI>
 * <code>com.opendxl.client.message</code>: See this package for information on the different types of messages that
 *      can be exchanged over the DXL fabric
 * </LI>
 * <LI>
 * <code>com.opendxl.client.callback</code>: See this package for information on registering "callbacks" that are used
 *      to receive messages via the {@link DxlClient}.
 * </LI>
 * <LI>
 * {@link ServiceRegistrationInfo}: This class holds the information necessary to register a service with the DXL
 *      fabric.
 * </LI>
 * </UL>
 */
public class DxlClient implements AutoCloseable {

    /**
     * The logger
     */
    private static Logger logger = Logger.getLogger(DxlClient.class);

    /**
     * The default "reply-to" prefix. This is typically used for setting up response
     * topics for requests, etc.
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

    /**
     *  The SSL socket factory
     */
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
     * A {@link Set} containing the topics that the client is currently subscribed to
     *
     * See {@link #subscribe} for more information on adding subscriptions
     */
    private final Set<String> subscriptions = new HashSet<>();

    /**
     * The default wait time for a synchronous request, defaults to 1 hour
     */
    private long defaultWait =
        Long.parseLong(System.getProperty(Constants.SYSPROP_DEFAULT_WAIT, Long.toString(60 * 60 * 1000)));


    /**
     * The strategy to use when the client is disconnected from the fabric
     */
    private DisconnectedStrategy disconnectStrategy = new ReconnectDisconnectedStrategy();

    /**
     * The prefix for the message pool thread names
     */
    private String messagePoolPrefix;

    /**
     * The "reply-to" prefix, typically used for setting up response topics for requests, etc.
     */
    private String replyToTopic;

    /**
     * The underlying MQTT client instance
     */
    private MqttClient client = null;

    /**
     * Service to handle incoming messages
     */
    private ExecutorService messageExecutor;

    /**
     * ExecutorService for the mqttClient
     */
    private ScheduledExecutorService mqttClientExecutor;

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
     * Indicates if the client is attempting to connect
     */
    private boolean attemptingToConnect = false;

    /**
     * An optional SSL socket factory callback
     */
    private SslSocketFactoryCallback sslSocketFactoryCallback = null;

    /**
     * Constructor for the {@link DxlClient}
     *
     * @param config The DXL client configuration (see {@link DxlClientConfig})
     * @throws DxlException If a DXL exception occurs
     */
    public DxlClient(DxlClientConfig config)
        throws DxlException {
        this(config, true);
    }

    /**
     * Constructor for the {@link DxlClient}
     *
     * @param config The DXL client configuration (see {@link DxlClientConfig})
     * @param init Whether to initialize the client
     * @throws DxlException If a DXL exception occurs
     */
    protected DxlClient(DxlClientConfig config, boolean init)
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

        if (init) {
            init();
        }
    }


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
    protected void checkInitialized() throws DxlException {
        if (!this.init) {
            throw new DxlException("The client has not been initialized.");
        }
    }

    /**
     * Whether the client is currently connected to the DXL fabric.
     *
     * @return {@code true} if the client is connected, otherwise {@code false}
     */
    public boolean isConnected() {
        return (this.init && this.client != null && this.client.isConnected());
    }

    /**
     * Initializes the state of the client
     *
     * @throws DxlException If there is an error initializing the client
     */
    protected synchronized void init() throws DxlException {
        if (!this.init) {
            doInit();

            this.init = true;
        }
    }

    /**
     * Destroys the client (releases all associated resources).
     * <P>
     * <b>NOTE</b>: This method should rarely be called directly. Instead, the preferred way to construct the client is
     * with a <i>try-with-resource</i> statement. This ensures that resources associated with the client are properly
     * cleaned up when the block is exited as shown below:
     * </P>
     * <PRE>
     * try (DxlClient client = new DxlClient(config)) {
     *     client.connect();
     *
     *     // do work here
     * }
     * </PRE>
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
     * Attempts to connect the client to the DXL fabric.
     * <P>
     * This method does not return until either the client has connected to the fabric or it has exhausted the number
     * of retries configured for the client causing an exception to be raised.
     * </P>
     * <P>
     * Several attributes are available for controlling the client retry behavior:
     * </P>
     * <UL>
     * <LI>
     * {@link DxlClientConfig#getConnectRetries} : The maximum number of connection attempts for each {@link Broker}
     *      specified in the {@link DxlClientConfig}
     * </LI>
     * <LI>
     * {@link DxlClientConfig#getReconnectDelay} : The initial delay between retry attempts. The delay increases
     *      ("backs off") as subsequent connection attempts are made.
     * </LI>
     * <LI>
     * {@link DxlClientConfig#getReconnectBackOffMultiplier} : Multiples the current reconnect delay by this value on
     *      subsequent connect retries. For example, a current delay of 3 seconds with a multiplier of 2 would result
     *      in the next retry attempt being in 6 seconds.
     * </LI>
     * <LI>
     * {@link DxlClientConfig#getReconnectDelayRandom} : A randomness delay percentage (between 0.0 and 1.0) that is
     *      used to increase the current retry delay by a random amount for the purpose of preventing multiple clients
     *      from having the same retry pattern in the next retry attempt being in 6 seconds.
     * </LI>
     * <LI>
     * {@link DxlClientConfig#getReconnectDelayMax} : The maximum delay between retry attempts
     * </LI>
     * </UL>
     *
     * @throws DxlException If the client is unable to establish a connection or an error occurs
     */
    public final void connect() throws DxlException {
        connect(false);
    }

    /**
     * Connects to the DXL fabric
     *
     * @param reconnect whether this is a reconnect or not (retry counts are ignored on a reconnect)
     * @throws DxlException If a DXL exception occurs
     */
    protected void connect(boolean reconnect) throws DxlException {
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

                int retries = config.getConnectRetries();
                long retryDelay = config.getReconnectDelay();
                boolean firstAttempt = true;
                Exception latestEx = null;

                while (!this.interrupt.get()
                        && ((reconnect && config.isInfiniteReconnectRetries())
                            || config.getConnectRetries() == -1 || retries >= 0)) {
                    if (!firstAttempt) {
                        // Determine retry delay
                        final long reconnectDelayMax = config.getReconnectDelayMax();
                        retryDelay = (retryDelay > reconnectDelayMax ? reconnectDelayMax : retryDelay);
                        // Apply random after max (so we still have randomness, may exceed maximum)
                        retryDelay += ((config.getReconnectDelayRandom() * retryDelay) * Math.random());

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
                        retryDelay *= config.getReconnectBackOffMultiplier();
                    }

                    try {
                        doConnect(brokers);

                        // Restore connections
                        synchronized (this.subscriptions) {
                            // Get the reply topic
                            final String replyTopic = getReplyToTopic();
                            if (replyTopic != null) {
                                this.subscriptions.add(replyTopic);
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
     * Attempts to disconnect the client from the DXL fabric.
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

        synchronized (this) {
            this.serviceManager.onDisconnect();
            // Actually disconnect the MQTT client
            doDisconnect();
        }
    }

    /**
     * Disconnects from the DXL fabric unconditionally.
     * <P>
     * A warning will still be logged.
     * </P>
     */
    private void disconnectQuietly() {
        try {
            disconnect();
        } catch (DxlException ex) {
            logger.warn("Failed attempt to disconnect", ex);
        }
    }

    /**
     * Attempts to reconnect the client to the fabric. When performing a reconnect, all existing topic subscriptions
     * are restored.
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
     * Returns the {@link Broker} that the client is currently connected to. {@code null} is returned if the client is
     * not currently connected to a {@link Broker}.
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
    protected synchronized void setCurrentBroker(final Broker broker) {
        this.currentBroker = broker;
    }

    /**
     * Resets the current broker
     */
    private void resetCurrentBroker() {
        setCurrentBroker(null);
    }

    /**
     * Subscribes to the specified topic on the DXL fabric. This method is typically used in conjunction with the
     * registration of {@link EventCallback} instances via the {@link #addEventCallback} method.
     * <P>
     * The following is a simple example of using this:
     * </P>
     * <PRE>
     * final EventCallback myEventCallback =
     *     event -&gt; {
     *         try {
     *             System.out.println("Received event: "
     *                 + new String(event.getPayload(), Message.CHARSET_UTF8));
     *         } catch (UnsupportedEncodingException ex) {
     *             ex.printStackTrace();
     *         }
     *     };
     * client.addEventCallback("/testeventtopic", myEventCallback, false);
     * client.subscribe("/testeventtopic");
     * </PRE>
     * <P>
     * <b>NOTE</b>: By default when registering an event callback the client will automatically subscribe to the
     * topic. In this example the {@link DxlClient#addEventCallback} method is invoked with the
     * <code>subscribeToTopic</code> parameter set to {@code false} preventing the automatic subscription.
     * </P>
     *
     * @param topic The topic to subscribe to
     * @throws DxlException If a DXL exception occurs
     */
    public final void subscribe(final String topic) throws DxlException {
        synchronized (this.subscriptions) {
            this.subscriptions.add(topic);
        }

        if (isConnected()) {
            doSubscribe(topic);
        }
    }

    /**
     * Unsubscribes from the specified topic on the DXL fabric.
     * <P>
     * See the {@link #subscribe} method for more information on subscriptions.
     * </P>
     *
     * @param topic The topic to unsubscribe from
     * @throws DxlException If a DXL exception occurs
     */
    public final void unsubscribe(final String topic) throws DxlException {
        if (isConnected()) {
            doUnsubscribe(topic);
        }

        synchronized (this.subscriptions) {
            this.subscriptions.remove(topic);
        }
    }

    /**
     * Returns a {@link Set} containing the topics that the client is currently subscribed to
     *
     * @return A {@link Set} containing the topics that the client is currently subscribed to
     */
    public Set<String> getSubscriptions() {
        synchronized (this.subscriptions) {
            return this.subscriptions;
        }
    }

    /**
     * Sends a {@link Request} message to a remote DXL service.
     * <P>
     * See the {@link ServiceRegistrationInfo} class for more information on DXL services.
     * </P>
     * The default wait time is {@code 1} hour
     *
     * @param request The {@link Request} message to send to a remote DXL service
     * @return The {@link Response}
     * @throws DxlException If an error occurs or the operation times out
     * @see com.opendxl.client.exception.WaitTimeoutException
     */
    public Response syncRequest(final Request request) throws DxlException {
        return syncRequest(request, this.defaultWait);
    }

    /**
     * Sends a {@link Request} message to a remote DXL service.
     * <P>
     * See the {@link ServiceRegistrationInfo} class for more information on DXL services.
     * </P>
     *
     * @param request The {@link Request} message to send to a remote DXL service
     * @param waitMillis The amount of time (in milliseconds) to wait for the {@link Response} to the request.
     *                   If the timeout is exceeded an exception will be raised.
     * @return The {@link Response}
     * @throws DxlException If an error occurs or the operation times out
     * @see com.opendxl.client.exception.WaitTimeoutException
     */
    public Response syncRequest(final Request request, final long waitMillis) throws DxlException {
        checkInitialized();
        request.setSourceClientId(getUniqueId());
        return this.requestManager.syncRequest(request, waitMillis);
    }

    /**
     * Sends a {@link Request} message to a remote DXL service asynchronously. This method differs from
     * {@link #syncRequest} due to the fact that it returns to the caller immediately after delivering the
     * {@link Request} message to the DXL fabric (It does not wait for the corresponding {@link Response} to be
     * received).
     * <P>
     * An optional {@link ResponseCallback} can be specified. This callback will be invoked when the corresponding
     * {@link Response} message is received by the client.
     * </P>
     * <P>
     * See the {@link ServiceRegistrationInfo} class for more information on DXL services.
     * </P>
     *
     * @param request The {@link Request} message to send to a remote DXL service
     * @param callback  An optional {@link ResponseCallback} that will be invoked when the corresponding
     *      {@link Response} message is received by the client.
     * @param waitMillis The amount of time to wait for a {@link Response} before removing the callback
     * @throws DxlException If a DXL exception occurs
     */
    public void asyncRequest(final Request request, final ResponseCallback callback, final long waitMillis)
        throws DxlException {
        checkInitialized();
        request.setSourceClientId(getUniqueId());
        this.requestManager.asyncRequest(request, callback, waitMillis);
    }

    /**
     * Sends a {@link Request} message to a remote DXL service asynchronously. This method differs from
     * {@link #syncRequest} due to the fact that it returns to the caller immediately after delivering the
     * {@link Request} message to the DXL fabric (It does not wait for the corresponding {@link Response} to be
     * received).
     * <P>
     * An optional {@link ResponseCallback} can be specified. This callback will be invoked when the corresponding
     * {@link Response} message is received by the client.
     * </P>
     * <P>
     * See the {@link ServiceRegistrationInfo} class for more information on DXL services.
     * </P>
     * The default wait time is {@code 1} hour. After the wait time is exceeded the callback will be removed
     * (no longer tracked).
     *
     * @param request The {@link Request} message to send to a remote DXL service
     * @param callback  An optional {@link ResponseCallback} that will be invoked when the corresponding
     *      {@link Response} message is received by the client.
     * @throws DxlException If a DXL exception occurs
     */
    public void asyncRequest(final Request request, final ResponseCallback callback) throws DxlException {
        asyncRequest(request, callback, this.defaultWait);
    }

    /**
     * Sends a {@link Request} message to a remote DXL service asynchronously. This method differs from
     * {@link #syncRequest} due to the fact that it returns to the caller immediately after delivering the
     * {@link Request} message to the DXL fabric (It does not wait for a {@link Response} to be received).
     *
     * @param request The {@link Request} message to send to a remote DXL service
     * @throws DxlException If a DXL exception occurs
     */
    public void asyncRequest(final Request request) throws DxlException {
        asyncRequest(request, null);
    }

    /**
     * Attempts to deliver the specified {@link Event} message to the DXL fabric.
     * <P>
     * See the {@link Message} class for more information on message types, how they are delivered to remote
     * clients, etc.
     * </P>
     *
     * @param event The {@link Event} to send
     * @throws DxlException If a DXL exception occurs
     */
    public void sendEvent(final Event event) throws DxlException {
        checkInitialized();
        event.setSourceClientId(getUniqueId());
        doSendEvent(event, event.getDestinationTopic(), packMessage(event));
    }

    /**
     * Attempts to deliver the specified {@link Response} message to the DXL fabric. The fabric will in turn attempt
     * to deliver the response back to the client who sent the corresponding {@link Request}.
     * <P>
     * See the {@link Message} class for more information on message types, how they are delivered to remote
     * clients, etc.
     * </P>
     * <P>
     * See the {@link ServiceRegistrationInfo} class for more information on DXL services.
     * </P>
     *
     * @param response The response to send
     * @throws DxlException If a DXL exception occurs
     */
    public void sendResponse(final Response response) throws DxlException {
        checkInitialized();
        response.setSourceClientId(getUniqueId());
        doSendResponse(response, response.getDestinationTopic(), packMessage(response));
    }

    /**
     * Adds a {@link RequestCallback} to the client for the specified topic. The callback will be invoked when
     * {@link Request} messages are received by the client on the specified topic. A topic of {@code null} indicates
     * that the callback should receive {@link Request} messages for all topics (no filtering).
     * <P>
     * <b>NOTE</b>: Usage of this method is quite rare due to the fact that registration of {@link RequestCallback}
     * instances with the client occurs automatically when registering a service. See the
     * {@link ServiceRegistrationInfo} class for more information on DXL services.
     * </P>
     *
     * @param topic The topic to receive {@link Request} messages on. A topic of {@code null} indicates that the
     *              callback should receive {@link Request} messages for all topics (no filtering).
     * @param callback The {@link RequestCallback} to be invoked when a {@link Request} message is received on the
     *                 specified topic.
     */
    public void addRequestCallback(final String topic, final RequestCallback callback) {
        this.requestCallbacks.addCallback(topic, callback);
    }

    /**
     * Removes a {@link RequestCallback} from the client for the specified topic. This method must be invoked with the
     * same arguments as when the callback was originally registered via {@link #addRequestCallback}.
     *
     * @param topic The topic to remove the callback for
     * @param callback The {@link RequestCallback} to be removed for the specified topic
     * @see #addRequestCallback(String, RequestCallback)
     */
    public void removeRequestCallback(final String topic, final RequestCallback callback) {
        this.requestCallbacks.removeCallback(topic, callback);
    }

    /**
     * Adds a {@link ResponseCallback} to the client for the specified topic. The callback will be invoked when
     * {@link Response} messages are received by the client on the specified topic. A topic of {@code null} indicates
     * that the callback should receive {@link Response} messages for all topics (no filtering).
     * <P>
     * <b>NOTE</b>: Usage of this method is quite rare due to the fact that the use of {@link ResponseCallback}
     * instances are typically limited to invoking a remote DXL service via the {@link #asyncRequest} method.
     * </P>
     *
     * @param topic The topic to receive {@link Request} messages on. A topic of {@code null} indicates that the
     *              callback should receive {@link Request} messages for all topics (no filtering).
     * @param callback The {@link RequestCallback} to be invoked when a {@link Request} message is received on the
     *                 specified topic.
     */
    public void addResponseCallback(final String topic, final ResponseCallback callback) {
        this.responseCallbacks.addCallback(topic, callback);
    }

    /**
     * Removes a {@link RequestCallback} from the client for the specified topic. This method must be invoked with the
     * same arguments as when the callback was originally registered via {@link #addRequestCallback}.
     *
     * @param topic The topic to remove the callback for
     * @param callback The {@link RequestCallback} to be removed for the specified topic
     * @see #addResponseCallback(String, ResponseCallback)
     */
    public void removeResponseCallback(final String topic, final ResponseCallback callback) {
        this.responseCallbacks.removeCallback(topic, callback);
    }

    /**
     * Adds a {@link EventCallback} to the client for the specified topic. The callback will be invoked when
     * {@link Event} messages are received by the client on the specified topic. A topic of {@code null} indicates that
     * the callback should receive {@link Event} messages for all topics (no filtering).
     *
     * @param topic The topic to receive {@link Event} messages on. A topic of {@code null} indicates that the callback
     *              should receive {@link Event} messages for all topics (no filtering).
     * @param callback The {@link EventCallback} to be invoked when a {@link Event} message is received on the
     *                 specified topic.
     * @param subscribeToTopic Indicates if the client should automatically subscribe ({@link #subscribe}) to the topic.
     * @throws DxlException If an error occurs
     */
    public void addEventCallback(final String topic, final EventCallback callback, final boolean subscribeToTopic)
        throws DxlException {
        this.eventCallbacks.addCallback(topic, callback);
        if (topic != null && subscribeToTopic) {
            this.subscribe(topic);
        }
    }

    /**
     * Adds a {@link EventCallback} to the client for the specified topic. The callback will be invoked when
     * {@link Event} messages are received by the client on the specified topic. A topic of {@code null} indicates that
     * the callback should receive {@link Event} messages for all topics (no filtering).
     * <P>
     * This variant of the {@code "addEventCallback"} methods will automatically subscribe ({@link #subscribe}) to the
     * topic. Use the {@link #addEventCallback(String, EventCallback, boolean)} method variant with the
     * {@code subscribeToTopic} parameter set to {@code false} to prevent automatically subscribing to the topic.
     * </P>
     *
     * @param topic The topic to receive {@link Event} messages on. A topic of {@code null} indicates that the callback
     *              should receive {@link Event} messages for all topics (no filtering).
     * @param callback The {@link EventCallback} to be invoked when a {@link Event} message is received on the
     *                 specified topic.
     * @throws DxlException If an error occurs
     */
    public void addEventCallback(final String topic, final EventCallback callback) throws DxlException {
        this.addEventCallback(topic, callback, true);
    }

    /**
     * Removes a {@link EventCallback} from the client for the specified topic. This method must be invoked with the
     * same arguments as when the callback was originally registered via {@link #addEventCallback}.
     *
     * @param topic The topic to remove the callback for
     * @param callback The {@link EventCallback} to be removed for the specified topic
     * @param unsubscribeFromTopic Indicates if the client should also unsubscribe ((@link #unsubscribe)) from the
     *                             topic.
     * @throws DxlException If an error occurs
     * @see #addEventCallback(String, EventCallback, boolean)
     */
    public void removeEventCallback(final String topic, final EventCallback callback,
                                    final boolean unsubscribeFromTopic) throws DxlException {
        this.eventCallbacks.removeCallback(topic, callback);
        if (topic != null && unsubscribeFromTopic) {
            this.unsubscribe(topic);
        }
    }

    /**
     * Removes a {@link EventCallback} from the client for the specified topic. This method must be invoked with the
     * same arguments as when the callback was originally registered via {@link #addEventCallback}.
     * <P>
     * This variant of the {@code "removeEventCallback"} methods will automatically unsubscribe ({@link #unsubscribe})
     * to the topic. Use the {@link #removeEventCallback(String, EventCallback, boolean)} method variant with the
     * {@code unsubscribeToTopic} parameter set to {@code false} to prevent automatically unsubscribing to the topic.
     * </P>
     *
     * @param topic The topic to remove the callback for
     * @param callback The {@link EventCallback} to be removed for the specified topic
     * @throws DxlException If an error occurs
     * @see #addEventCallback(String, EventCallback)
     */
    public void removeEventCallback(final String topic, final EventCallback callback) throws DxlException {
        this.removeEventCallback(topic, callback, true);
    }

    /**
     * Registers a DXL service with the fabric. The specified {@link ServiceRegistrationInfo} instance contains
     * information about the service that is to be registered.
     * <P>
     * This method will wait for confirmation of the service registration for up to the specified timeout in
     * milliseconds. If the timeout is exceeded an exception will be raised.
     * </P>
     * <P>
     * See the {@link ServiceRegistrationInfo} class for more information on DXL services.
     * </P>
     *
     * @param service A {@link ServiceRegistrationInfo} instance containing information about the service that is to
     *                be registered.
     * @param timeout The amount of time (in milliseconds) to wait for confirmation of the service registration. If
     *                the timeout is exceeded an exception will be raised.
     * @throws DxlException If an error occurs
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
     * Registers a DXL service with the fabric asynchronously. The specified {@link ServiceRegistrationInfo} instance
     * contains information about the service that is to be registered.
     * <P>
     * This method differs from {@link #registerServiceSync} due to the fact that it returns to the caller immediately
     * after sending the registration message to the DXL fabric (It does not wait for registration confirmation
     * before returning).
     * </P>
     * <P>
     * See the {@link ServiceRegistrationInfo} class for more information on DXL services.
     * </P>
     *
     * @param service A {@link ServiceRegistrationInfo} instance containing information about the service that is to
     *                be registered.
     * @throws DxlException If an error occurs
     */
    public void registerServiceAsync(ServiceRegistrationInfo service) throws DxlException {
        this.serviceManager.addService(service);
    }

    /**
     * Unregisters (removes) a DXL service from the fabric. The specified {@link ServiceRegistrationInfo} instance
     * contains information about the service that is to be removed.
     * <P>
     * This method will wait for confirmation of the service unregistration for up to the specified timeout in
     * milliseconds. If the timeout is exceeded an exception will be raised.
     * </P>
     * <P>
     * See the {@link ServiceRegistrationInfo} class for more information on DXL services.
     * </P>
     *
     * @param service A {@link ServiceRegistrationInfo} instance containing information about the service that is to
     *                be unregistered.
     * @param timeout The amount of time (in milliseconds) to wait for confirmation of the service unregistration. If
     *                the timeout is exceeded an exception will be raised.
     * @throws DxlException If an error occurs
     */
    public void unregisterServiceSync(final ServiceRegistrationInfo service, final long timeout)
        throws DxlException {
        if (!isConnected()) {
            throw new DxlException("The client is not currently connected");
        }

        if (service == null) {
            throw new IllegalArgumentException("Undefined service object");
        }

        this.serviceManager.removeService(service.getServiceId());
        service.waitForUnregistration(timeout);
    }

    /**
     * Unregisters (removes) a DXL service with from the fabric asynchronously. The specified
     * {@link ServiceRegistrationInfo} instance contains information about the service that is to be removed.
     * <P>
     * This method differs from {@link #unregisterServiceSync} due to the fact that it returns to the caller
     * immediately after sending the unregistration message to the DXL fabric (It does not wait for
     * unregistration confirmation before returning).
     * </P>
     * <P>
     * See the {@link ServiceRegistrationInfo} class for more information on DXL services.
     * </P>
     *
     * @param service A {@link ServiceRegistrationInfo} instance containing information about the service that is to
     *                be unregistered.
     * @throws DxlException If an error occurs
     */
    public void unregisterServiceAsync(ServiceRegistrationInfo service) throws DxlException {
        this.serviceManager.removeService(service.getServiceId());
    }

    /**
     * Unregisters (removes) a DXL service with from the fabric asynchronously. The specified
     * service id of a service will be removed.
     * <P>
     * This method differs from {@link #unregisterServiceSync} due to the fact that it returns to the caller
     * immediately after sending the unregistration message to the DXL fabric (It does not wait for
     * unregistration confirmation before returning).
     * </P>
     * <P>
     * See the {@link ServiceRegistrationInfo} class for more information on DXL services.
     * </P>
     *
     * @param serviceId The service id of a service to be removed
     * @throws DxlException If an error occurs
     */
    public void unregisterServiceAsync(final String serviceId) throws DxlException {
        this.serviceManager.removeService(UuidGenerator.normalize(serviceId));
    }

    /**
     * Fires the specified {@link Event} to {@link EventCallback} listeners currently registered with the client.
     *
     * @param event The {@link Event} to fire.
     * @see #addEventCallback
     */
    private void fireEvent(final Event event) {
        this.eventCallbacks.fireMessage(event);
    }

    /**
     * Fires the specified {@link Response} to {@link ResponseCallback} listeners currently registered with the client.
     *
     * @param response The {@link Response} to fire.
     * @see #addResponseCallback
     */
    private void fireResponse(final Response response) {
        this.responseCallbacks.fireMessage(response);
    }

    /**
     * Fires the specified {@link Request} to {@link RequestCallback} listeners currently registered with the client.
     *
     * @param request The {@link Request} to fire.
     * @see #addRequestCallback
     */
    private void fireRequest(final Request request) {
        this.requestCallbacks.fireMessage(request);
    }

    /**
     * Invoked when the client has been disconnected by the broker.
     */
    private void handleDisconnected() {
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
     * Sets the strategy to use if the client becomes unexpectedly disconnected from the broker.
     * By default the {@link ReconnectDisconnectedStrategy} is used.
     *
     * @param strategy The strategy to use if the client becomes unexpectedly disconnected
     *                 from the broker. By default the {@link ReconnectDisconnectedStrategy} is used.
     */
    public void setDisconnectedStrategy(final DisconnectedStrategy strategy) {
        this.disconnectStrategy = strategy;
    }

    /**
     * Sets the SSL socket factory
     *
     * @param socketFactory The SSL socket factory
     */
    public void setSocketFactory(SSLSocketFactory socketFactory) {
        this.socketFactory = socketFactory;
    }

    /**
     * Processes an incoming message. The bytes from the message are converted into the appropriate message type
     * instance (request, response, event, etc.) and then the corresponding registered message callbacks are notified.
     *
     * @param topic The topic that the message arrived on
     * @param bytes The message received (as bytes)
     */
    private void handleMessage(final String topic, final byte[] bytes) throws IOException {
        final Message message = Message.fromBytes(bytes);
        // Set the topic that the message was delivered on
        message.setDestinationTopic(topic);
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
        doSendRequest(request, request.getDestinationTopic(), packMessage(request));
    }

    /**
     * Packs the specified message (converts the message to a byte array) for transmitting over the DXL fabric.
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
     * Returns the name of the "reply-to" topic to use for communicating back to this client
     * (responses to requests).
     *
     * @return The name of the "reply-to" topic to use for communicating back to this client
     * (responses to requests).
     */
    public String getReplyToTopic() {
        return this.replyToTopic;
    }

    /**
     * Set the name of the "reply-to" topic to use for communicating back to this client
     * (responses to requests).
     *
     * @param replyToTopic The name of the "reply-to" topic to use for communicating back to this client
     */
    protected void setReplyToTopic(String replyToTopic) {
        this.replyToTopic = replyToTopic;
    }

    /**
     * Returns the {@link DxlClientConfig} assoicated with the client
     *
     * @return The {@link DxlClientConfig} assoicated with the client
     */
    public DxlClientConfig getConfig() {
        return this.config;
    }

    /**
     * Return whether the invoking thread as the "incoming message" thread
     *
     * @return Whether the invoking thread as the "incoming message" thread
     */
    protected boolean isIncomingMessageThread() {
        return Thread.currentThread().getName().startsWith(this.messagePoolPrefix);
    }

    /**
     * Creates a new MQTT client (replaces existing)
     *
     * @param serverUri The server URI
     * @throws MqttException An MQTT exception
     */
    private void resetClient(final String serverUri) throws MqttException {
        this.client = new MqttClient(serverUri, this.getUniqueId(), new MemoryPersistence(), mqttClientExecutor);

        // This is a global operation timeout
        this.client.setTimeToWait(getConfig().getOperationTimeToWait());
    }

    /**
     * Performs client initialization
     */
    private synchronized void doInit() throws DxlException {
        DxlClientConfig config = getConfig();

        if (config == null) {
            throw new DxlException("No client configuration");
        }
        if (config.getInUseBrokerList() == null || config.getInUseBrokerList().isEmpty()) {
            throw new DxlException("No broker defined");
        }

        final KeyStore ks = config.getKeyStore();

        try {
            if (ks != null && this.sslSocketFactoryCallback == null) {
                this.socketFactory = SSLValidationSocketFactory.newInstance(ks, DxlClientConfig.KS_PASS);
            }
            //
            // Each thread is a daemon thread.
            //
            this.mqttClientExecutor = java.util.concurrent.Executors.newScheduledThreadPool(10,
                    new ThreadFactory() {
                        public Thread newThread(Runnable r) {
                            Thread t = java.util.concurrent.Executors.defaultThreadFactory().newThread(r);
                            t.setDaemon(true);
                            return t;
                        }
                    });

            // Use the first broker in the list for initialization. This will be overwritten before connect.
            Broker broker = config.getInUseBrokerList().get(0);
            resetClient(broker.toString());

            // The reply-to topic name
            this.replyToTopic = replyToPrefix + this.getUniqueId();

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
                        if (this.mqttClientExecutor != null) {
                            this.mqttClientExecutor.shutdown();
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
     *
     * @param brokers The brokers to attempt to connect to
     */
    private synchronized void doConnect(final Map<String, Broker> brokers)
        throws DxlException {
        Exception latestEx = null;

        try {
            doDisconnectQuietly();
            this.client.setCallback(new DxlMqttCallback());

            final MqttConnectOptions connectOps = new MqttConnectOptions();

            connectOps.setCleanSession(true);
            connectOps.setKeepAliveInterval(getConfig().getKeepAliveInterval());
            connectOps.setConnectionTimeout(this.getConfig().getConnectTimeout());
            //setting to version 3.1.1 to prevent repeated connect with earlier MQTT version
            connectOps.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);

            connectOps.setHttpsHostnameVerificationEnabled(getConfig().isHttpsHostnameVerificationEnabled());

            // Set socket factory if applicable
            if (this.sslSocketFactoryCallback != null) {
                connectOps.setSocketFactory(this.sslSocketFactoryCallback.createFactory(getConfig()));
            } else {
                connectOps.setSocketFactory(socketFactory);
            }

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
                dt.join(this.getConfig().getDisconnectTimeout() * 1000);

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
     * Disconnects from the DXL fabric unconditionally. A warning will still be logged.
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
     * Subscribes to a topic on the DXL fabric
     *
     * @param topic The topic
     */
    private void doSubscribe(final String topic)
        throws DxlException {
        try {
            this.client.subscribe(topic);
        } catch (final Exception ex) {
            throw new DxlException("Error during subscribe", ex);
        }
    }

    /**
     * Unsubscribes from a topic on the DXL fabric
     *
     * @param topic The topic
     */
    private void doUnsubscribe(final String topic)
        throws DxlException {
        try {
            this.client.unsubscribe(topic);
        } catch (final Exception ex) {
            throw new DxlException("Error during subscribe", ex);
        }
    }

    /**
     * Publishes the specified message
     *
     * @param topic The topic to publish on
     * @param bytes The message content
     * @param qos The quality of service (QOS)
     */
    private void publishMessage(final String topic, final byte[] bytes, int qos)
        throws DxlException {
        try {
            this.client.publish(topic, bytes, qos, false);
        } catch (final Exception ex) {
            throw new DxlException("Error publishing message", ex);
        }
    }

    /**
     * Sends a request to the DXL fabric
     *
     * @param request The Request
     * @param topic The topic
     * @param bytes The message content
     */
    private void doSendRequest(final Request request, final String topic, final byte[] bytes)
        throws DxlException {
        publishMessage(topic, bytes, 0 /* Only supports 0 currently */);
    }

    /**
     * Sends a response to the DXL fabric
     *
     * @param response The response
     * @param topic The topic
     * @param bytes The message content
     */
    private void doSendResponse(final Response response, final String topic, final byte[] bytes)
        throws DxlException {
        publishMessage(topic, bytes, 0 /* Only supports 0 currently */);
    }

    /**
     * Sends an event to the DXL fabric (to be implemented by concrete classes).
     *
     * @param event The event
     * @param topic The topic
     * @param bytes The message content
     */
    private void doSendEvent(final Event event, final String topic, final byte[] bytes)
        throws DxlException {
        publishMessage(topic, bytes, 0 /* Only supports 0 currently */);
    }

    /**
     * The purpose of this method is to allow to specify the appropriate "reply-to"
     * path {@link Request#setReplyToTopic(String)} for the specified {@link Request}.
     *
     * @param request The {@link Request} to set the "reply-to" on
     */
    private void setReplyToForMessage(final Request request)
        throws DxlException {
        request.setReplyToTopic(this.replyToTopic);
    }

    /**
     * Returns the count of async callbacks that are waiting for a response
     *
     * @return The count of async callbacks that are waiting for a response
     */
    protected int getAsyncCallbackCount() {
        return this.requestManager.getAsyncCallbackCount();
    }

    /**
     * Returns information related to currently active services
     *
     * @return Information related to currently active services
     */
    protected List<Map<String, Object>> getActiveServices() {
        return serviceManager.getActiveServices();
    }

    /**
     * Sets an optional {@link SslSocketFactoryCallback}. This callback will be invoked prior
     * to an SSL connection being established.
     *
     * @param cb The callback
     */
    protected void setSslSocketFactoryCallback(final SslSocketFactoryCallback cb) {
        this.sslSocketFactoryCallback = cb;
    }

    /**
     * Interface that allows for the {@link SSLSocketFactory} to be replaced.
     */
    protected interface SslSocketFactoryCallback {
        /**
         * Invoked to create an {@link SSLSocketFactory} prior to connecting to a fabric.
         *
         * @param config The client configuration
         * @return The {@link SSLSocketFactory}
         * @throws Exception If an error occurs
         */
        SSLSocketFactory createFactory(DxlClientConfig config) throws Exception;
    };

    /**
     * Implements the {@link MqttCallback} interface (used to received callbacks from the MQTT client.
     */
    private class DxlMqttCallback implements MqttCallback {

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
        public void messageArrived(final String topic, final MqttMessage message) {
            if (logger.isDebugEnabled()) {
                logger.debug("messageArrived: " + topic);
            }

            // Handle any received messages on a separate thread.
            // If you invoke any MQTT client-related methods (subscribe/un-subscribe, etc.)
            // during a handled message, deadlock will occur. The received messages
            // are on the "MQTT Call" thread.
            messageExecutor.execute(
                () -> {
                    try {
                        handleMessage(topic, message.getPayload());
                    } catch (Throwable t) {
                        logger.error("Error during message handling", t);
                    }
                }
            );
        }
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
