package com.opendxl.client;

public abstract class Constants {
    /**
     * The system property for specifying connect retries
     */
    public static final String SYSPROP_CONNECT_RETRIES = "dxlClient.connectRetries";
    /**
     * The system property for the amount of time (in ms) for the first connect retry
     */
    public static final String SYSPROP_RECONNECT_DELAY = "dxlClient.reconnectDelay";
    /**
     * The system property for specifying the maximum reconnect delay
     */
    public static final String SYSPROP_MAX_RECONNECT_DELAY = "dxlClient.maxReconnectDelay";
    /**
     * The system property for specifying the reconnect delay randomness
     */
    public static final String SYSPROP_RECONNECT_DELAY_RANDOM = "dxlClient.reconnectDelayRandom";
    /**
     * The system property for specifying the reconnect back off multiplier
     */
    public static final String SYSPROP_RECONNECT_BACK_OFF_MULTIPLIER = "dxlClient.reconnectBackOffMultiplier";
    /**
     * The system property for specifying the default wait time (for sync request, etc.)
     */
    public static final String SYSPROP_DEFAULT_WAIT = "dxlClient.defaultWait";
    /**
     * The system property for specifying the default query timeout (for broker query request
     */
    public static final String SYSPROP_DEFAULT_QUERY_TIMEOUT = "dxlClient.defaultQueryTimeout";
    /**
     * The system property for specifying the interval for checking async callbacks for timeout
     */
    public static final String SYSPROP_ASYNC_CALLBACK_CHECK_INTERVAL = "dxlClient.asyncCallbackCheckInterval";
    /**
     * This system property can be set to "false" if the reconnect behavior should
     * be disabled when the client is unexpectedly disconnected from the broker.
     * <p>
     * This is equivalent to settings {@link DxlClient#setDisconnectedStrategy(DisconnectedStrategy)}
     * to {@code null}
     * </p>
     */
    public static final String SYSPROP_DISABLE_DISCONNECTED_STRATEGY = "dxlClient.disableDisconnectedStrategy";

    /**
     * This system property defines the default Time-To-Live (TTL) of the service registration
     */
    public static final String SYSPROP_SERVICE_TTL_DEFAULT = "dxlClient.service.ttlDefault";
    /**
     * This system property defines the Time-To-Live (TTL) grace period of the service registration
     */
    public static final String SYSPROP_SERVICE_TTL_GRACE_PERIOD = "dxlClient.service.ttlGracePeriod";
    /**
     * This system property defines the minimum Time-To-Live (TTL) of the service registration
     */
    public static final String SYSPROP_SERVICE_TTL_LOWER_LIMIT = "dxlClient.service.ttlLowerLimit";
    /**
     * This system property defines the Time-To-Live (TTL) resolution (FOR TESTING ONLY)
     */
    public static final String SYSPROP_SERVICE_TTL_RESOLUTION = "dxlClient.service.ttlResolution";


    /**
     * The system property for specifying connection timeout (in ms)
     */
    public static final String SYSPROP_CONNECT_TIMEOUT = "dxlClient.brokerConnectTimeout";
    /**
     * The system property for a keep alive interval (minutes) (client pings broker at interval)
     */
    public static final String SYSPROP_MQTT_KEEP_ALIVE_INTERVAL = "dxlClient.mqtt.keepAliveInterval";
    /**
     * The thread pool size to handle incoming messages
     */
    public static final String SYSPROP_INCOMING_MESSAGE_THREAD_POOL_SIZE = "dxlClient.incomingMessageThreadPoolSize";
    /**
     * The thread pool queue size to handle incoming messages
     */
    public static final String SYSPROP_INCOMING_MESSAGE_QUEUE_SIZE = "dxlClient.incomingMessageQueueSize";

    /**
     * The topic notified when services are registered
     */
    public static final String DXL_SERVICE_REGISTER_TOPIC = "/mcafee/event/dxl/svcregistry/register";
    /**
     * The topic to publish a service registration request to
     */
    public static final String DXL_SERVICE_REGISTER_REQUEST_TOPIC = "/mcafee/service/dxl/svcregistry/register";
    /**
     * The topic notified when services are unregistered
     */
    public static final String DXL_SERVICE_UNREGISTER_TOPIC = "/mcafee/event/dxl/svcregistry/unregister";
    /**
     * The topic to publish a service unregistration request to
     */
    public static final String DXL_SERVICE_UNREGISTER_REQUEST_TOPIC = "/mcafee/service/dxl/svcregistry/unregister";
    /**
     * The topic to query the service registry
     */
    public static final String DXL_SERVICE_QUERY_TOPIC = "/mcafee/service/dxl/svcregistry/query";
    /**
     * The topic to query the client registry
     */
    public static final String DXL_CLIENT_QUERY_TOPIC = "/mcafee/service/dxl/clientregistry/query";
    /**
     * The topic to receive client connect events
     */
    public static final String DXL_CLIENT_CONNECT_TOPIC = "/mcafee/event/dxl/clientregistry/connect";
    /**
     * The topic to receive client disconnect events
     */
    public static final String DXL_CLIENT_DISCONNECT_TOPIC = "/mcafee/event/dxl/clientregistry/disconnect";
    /**
     * The topic to query the broker registry
     */
    public static final String DXL_BROKER_REGISTER_TOPIC = "/mcafee/service/dxl/brokerregistry/query";

    ////////////////////////////////////////////////////////////////////////////
    // MQTT Specific Properties
    ////////////////////////////////////////////////////////////////////////////

    /**
     * The system property for the connect timeout
     */
    public static final String MQTT_CONNECT_TIMEOUT = "dxlClient.mqtt.connectTimeout";

    /**
     * The system property for the connect timeout
     */
    public static final String MQTT_DISCONNECT_TIMEOUT = "dxlClient.mqtt.disconnectTimeout";

    /**
     * The system property for specifying the time to wait for an operation to complete
     */
    public static final String MQTT_TIME_TO_WAIT = "dxlClient.mqtt.timeToWait";
}
