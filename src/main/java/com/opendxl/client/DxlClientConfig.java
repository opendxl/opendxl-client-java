/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client;

import com.opendxl.client.exception.DxlException;
import com.opendxl.client.util.UuidGenerator;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * The {@link DxlClientConfig} class holds the information necessary to connect a {@link DxlClient} to the DXL fabric.
 */
public class DxlClientConfig {

    /**
     *  The default keystore password
     */
    public static final String KS_PASS = "";

    //
    // INI Sections
    //
    private static final String GENERAL_INI_SECTION = "General";
    private static final String CERTS_INI_SECTION = "Certs";
    private static final String BROKERS_INI_SECTION = "Brokers";
    private static final String WEBSOCKET_BROKERS_INI_SECTION = "BrokersWebSockets";
    private static final String PROXY_INI_SECTION = "Proxy";

    //
    // INI Keys
    //
    private static final String BROKER_CERT_INI_CHAIN_KEY_NAME = "BrokerCertChain";
    private static final String CERT_FILE_INI_KEY_NAME = "CertFile";
    private static final String PRIVATE_KEY_INI_KEY_NAME = "PrivateKey";
    private static final String USE_WEBSOCKETS_INI_KEY_NAME = "UseWebSockets";
    private static final String PROXY_ADDRESS = "Address";
    private static final String PROXY_PORT = "Port";
    private static final String PROXY_USER_NAME = "User";
    private static final String PROXY_USER_PASSWORD = "Password";

    /**
     * A mapping of various strings to boolean values
     */
    private static final Map<String, Boolean> stringToBooleanMap;

    static {
        Map<String, Boolean> tempStringToBooleanMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        tempStringToBooleanMap.put("yes", Boolean.TRUE);
        tempStringToBooleanMap.put("no", Boolean.FALSE);
        tempStringToBooleanMap.put("on", Boolean.TRUE);
        tempStringToBooleanMap.put("off", Boolean.FALSE);
        tempStringToBooleanMap.put("1", Boolean.TRUE);
        tempStringToBooleanMap.put("0", Boolean.FALSE);
        tempStringToBooleanMap.put("true", Boolean.TRUE);
        tempStringToBooleanMap.put("false", Boolean.FALSE);
        stringToBooleanMap = Collections.unmodifiableMap(tempStringToBooleanMap);
    }

    /**
     * The unique identifier of the client
     */
    private String uniqueId;

    /**
     * The list of brokers supporting standard MQTT connections
     */
    private List<Broker> brokers;

    /**
     * The list of brokers supporting DXL connections over WebSockets
     */
    private List<Broker> webSocketBrokers;

    /**
     * The keystore
     */
    private KeyStore keystore;

    /**
     * The file name of a bundle containing the broker CA certificates in PEM format
     */
    private String brokerCaBundlePath;

    /**
     * The filename of the client certificate in PEM format
     */
    private String certFile;

    /**
     * The filename of the client private key
     */
    private String privateKey;

    /**
     * The original file name of a bundle containing the broker CA certificates in PEM format
     */
    private String brokerCaBundlePathOriginal;

    /**
     * The original filename of the client certificate in PEM format
     */
    private String certFileOriginal;

    /**
     * The original filename of the client private key
     */
    private String privateKeyOriginal;

    /**
     * Whether to use WebSockets or regular MQTT over tcp
     */
    private boolean useWebSockets = false;

    /**
     * The number of times to retry during connect, default -1 (infinite)
     */
    private int connectRetries =
        Integer.parseInt(System.getProperty(Constants.SYSPROP_CONNECT_RETRIES, "-1"));

    /**
     * The reconnect delay (in ms), defaults to 1000ms (1 second)
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
     * Time to wait for an operation to complete (defaults to 2 minutes)
     */
    private long operationTimeToWait =
        Long.parseLong(
            System.getProperty(Constants.MQTT_TIME_TO_WAIT, Long.toString(120 * 1000)));

    /**
     * Connect timeout (defaults to 30 seconds)
     */
    private int connectTimeout =
        Integer.parseInt(
            System.getProperty(Constants.MQTT_CONNECT_TIMEOUT, Integer.toString(30)));

    /**
     * Disconnect timeout (defaults to 60 seconds)
     */
    private int disconnectTimeout =
        Integer.parseInt(
            System.getProperty(Constants.MQTT_DISCONNECT_TIMEOUT, Integer.toString(60)));

    /**
     * The broker ping timeout (in ms), defaults to 500ms
     */
    private int brokerPingTimeout =
        Integer.parseInt(System.getProperty(Constants.SYSPROP_CONNECT_TIMEOUT, "1000"));

    /**
     * Keep alive interval (30 minutes by default)
     */
    private int keepAliveInterval =
        Integer.parseInt(
            System.getProperty(Constants.SYSPROP_MQTT_KEEP_ALIVE_INTERVAL, Integer.toString(30 * 60)));

    /**
     * Incoming message thread pool size
     */
    private int incomingMessageThreadPoolSize =
        Integer.parseInt(
            System.getProperty(Constants.SYSPROP_INCOMING_MESSAGE_THREAD_POOL_SIZE, Integer.toString(1)));

    /**
     * Whether the client should infinitely retry to reconnect when it gets disconnected (defaults to true)
     */
    private boolean infiniteReconnect = true;

    /**
     * Whether SSL host name verification is enabled when connecting to a broker
     */
    private boolean httpsHostnameVerificationEnabled = false;

    /**
     * The logger
     */
    private static Logger logger = Logger.getLogger(DxlClientConfig.class);

    /**
     * Incoming message queue size (defaults to 16384)
     */
    private int incomingMessageQueueSize =
        Integer.parseInt(
            System.getProperty(Constants.SYSPROP_INCOMING_MESSAGE_QUEUE_SIZE, Integer.toString(16384)));

    /**
     * The HTTP proxy address
     */
    private String proxyAddress;

    /**
     * The HTTP proxy port
     */
    private int proxyPort;

    /**
     * The HTTP proxy user name
     */
    private String proxyUserName;

    /**
     * The HTTP proxy user password
     */
    private char[] proxyPassword;

    /**
     * Default constructor
     */
    protected DxlClientConfig() {
        this.uniqueId = UuidGenerator.generateIdAsString();
    }

    /**
     * Construct the configuration with a unique id
     *
     * @param uniqueId The unique identifier of the client
     */
    protected DxlClientConfig(final String uniqueId) {
        if (uniqueId == null || uniqueId.isEmpty()) {
            this.uniqueId = UuidGenerator.generateIdAsString();
        } else {
            this.uniqueId = uniqueId;
        }
    }

    /**
     * Constructs the configuration
     *
     * @param brokerCaBundlePath The file name of a bundle containing the broker CA certificates in PEM format
     * @param certFile The file name of the client certificate in PEM format
     * @param privateKey The file name of the client private key in PEM format
     * @param brokers A list of {@link Broker} objects representing brokers comprising the DXL fabric supporting
     *                standard MQTT connections. When invoking the {@link DxlClient#connect} method, the
     *                {@link DxlClient} will attempt to connect to the closest broker.
     */
    public DxlClientConfig(
        final String brokerCaBundlePath, final String certFile, final String privateKey,
        final List<Broker> brokers) {
        this(brokerCaBundlePath, certFile, privateKey, brokers, Collections.EMPTY_LIST);
    }

    /**
     * Constructs the configuration
     *
     * @param brokerCaBundlePath The file name of a bundle containing the broker CA certificates in PEM format
     * @param certFile The file name of the client certificate in PEM format
     * @param privateKey The file name of the client private key in PEM format
     * @param brokers A list of {@link Broker} objects representing brokers comprising the DXL fabric supporting
     *                standard MQTT connections. When invoking the {@link DxlClient#connect} method, the
     *                {@link DxlClient} will attempt to connect to the closest broker.
     * @param webSocketBrokers A list {@link Broker} objects representing brokers on the DXL fabric supporting DXL
     *                         connections over WebSockets. When invoking the {@link DxlClient#connect} method,
     *                         the {@link DxlClient} will attempt to connect to the closest broker.
     */
    public DxlClientConfig(
        final String brokerCaBundlePath, final String certFile, final String privateKey,
        final List<Broker> brokers, final List<Broker> webSocketBrokers) {
        this.uniqueId = UuidGenerator.generateIdAsString();
        this.brokers = brokers;
        this.brokerCaBundlePath = brokerCaBundlePath;
        this.brokerCaBundlePathOriginal = brokerCaBundlePath;
        this.certFileOriginal = certFile;
        this.certFile = certFile;
        this.privateKey = privateKey;
        this.privateKeyOriginal = privateKey;
        this.webSocketBrokers = webSocketBrokers;
        this.useWebSockets = false;
    }

    /**
     * Returns the {@link KeyStore} associated with the client configuration
     *
     * @return The {@link KeyStore} associated with the client configuration
     * @throws DxlException If an error occurs
     */
    public synchronized KeyStore getKeyStore() throws DxlException {
        if (this.keystore == null && this.brokerCaBundlePath != null && this.certFile != null
                && this.privateKey != null) {
            try {
                this.keystore = KeyStoreUtils.generateKeyStoreFromFiles(
                    this.brokerCaBundlePath, this.certFile, this.privateKey, KS_PASS);
            } catch (Exception ex) {
                throw new DxlException("Error building keystore", ex);
            }
        }
        return this.keystore;
    }

    /**
     * Returns the file name of a bundle containing the broker CA certificates in PEM format
     *
     * @return the file name of a bundle containing the broker CA certificates in PEM format
     */
    public String getBrokerCaBundlePath() {
        return brokerCaBundlePath;
    }

    /**
     * Returns the unique identifier of the client instance
     *
     * @return The unique identifier of the client instance
     */
    public String getUniqueId() {
        return uniqueId;
    }

    /**
     * Overwrites the unique identifier of the client with a new UUID.
     *
     * @param uniqueId The unique identifier of the client.
     */
    protected void setUniqueId(final String uniqueId) {
        this.uniqueId = uniqueId;
    }

    /**
     * Returns the list of {@link Broker} objects representing brokers on the DXL fabric supporting standard
     * MQTT connections. When invoking the {@link DxlClient#connect} method, the {@link DxlClient} will attempt to
     * connect to the closest broker.
     *
     * @return The list of {@link Broker} objects representing brokers on the DXL fabric supporting standard
     * MQTT connections.
     */
    public List<Broker> getBrokerList() {
        return brokers;
    }

    /**
     * Returns the list of {@link Broker} objects representing brokers on the DXL fabric. When invoking
     * the {@link DxlClient#connect} method, the {@link DxlClient} will attempt to
     * connect to the closest broker.
     *
     * @return The list of {@link Broker} objects representing brokers on the DXL fabric
     */
    public List<Broker> getInUseBrokerList() {
        if (useWebSockets) {
            return webSocketBrokers;
        }

        return brokers;
    }

    /**
     * Add a {@link Broker} object to the list of brokers on the DXL fabric supporting standard MQTT
     * connections.
     *
     * @param broker A {@link Broker} object
     */
    protected synchronized void addBroker(final Broker broker) {
        if (broker != null) {
            if (brokers == null) {
                brokers = new ArrayList<>();
            }
            brokers.add(broker);
        }
    }

    /**
     * Set the list of {@link Broker} objects representing brokers on the DXL fabric supporting standard
     * MQTT connections.
     *
     * @param brokers The list of {@link Broker} objects representing brokers on the DXL fabric supporting
     *                standard MQTT connections.
     */
    protected void setBrokers(List<Broker> brokers) {
        this.brokers = brokers;
    }

    /**
     * Returns the list of {@link Broker} objects representing brokers on the DXL fabric supporting DXL connections
     * over WebSockets. When invoking the {@link DxlClient#connect} method, the {@link DxlClient} will
     * attempt to connect to the closest broker.
     *
     * @return The list of {@link Broker} objects representing brokers on the DXL fabric supporting DXL connections
     * over WebSockets.
     */
    public List<Broker> getWebSocketBrokers() {
        return webSocketBrokers;
    }

    /**
     * Set the list of {@link Broker} objects representing brokers on the DXL fabric supporting DXL connections
     * over WebSockets.
     *
     * @param webSocketBrokers The list of {@link Broker} objects representing brokers on the DXL fabric supporting
     * DXL connections over WebSockets.
     */
    public void setWebSocketBrokers(List<Broker> webSocketBrokers) {
        this.webSocketBrokers = webSocketBrokers;
    }

    /**
     * Returns whether the client should use WebSockets or regular MQTT over tcp when connecting to a {@link Broker}
     *
     * <P>
     * Defaults to {@code false}
     * </P>
     *
     * @return Whether the client should use WebSockets or regular MQTT over tcp when connecting to a {@link Broker}
     */
    public boolean isUseWebSockets() {
        return useWebSockets;
    }

    /**
     * Sets whether the client should use WebSockets or regular MQTT over tcp when connecting to a {@link Broker}
     *
     * @param useWebSockets Whether the client should use WebSockets or regular MQTT over tcp when connecting to
     * a {@link Broker}
     */
    public void setUseWebSockets(boolean useWebSockets) {
        this.useWebSockets = useWebSockets;
    }

    /**
     * Sets the thread pool size for incoming messages
     * <P>
     * Defaults to {@code 1}
     * </P>
     *
     * @param size The thread pool size for incoming messages
     */
    public void setIncomingMessageThreadPoolSize(final int size) {
        this.incomingMessageThreadPoolSize = size;
    }

    /**
     * Returns the thread pool size for incoming messages
     * <P>
     * Defaults to {@code 1}
     * </P>
     *
     * @return The thread pool size for incoming messages
     */
    public int getIncomingMessageThreadPoolSize() {
        return this.incomingMessageThreadPoolSize;
    }

    /**
     * Sets the queue size for incoming messages (will block when queue is full)
     * <P>
     * Defaults to {@code 16384}
     * </P>
     *
     * @param size The queue size for incoming messages
     */
    public void setIncomingMessageQueueSize(final int size) {
        this.incomingMessageQueueSize = size;
    }

    /**
     * Returns the queue size for incoming messages (will block when queue is full)
     * <P>
     * Defaults to {@code 16384}
     * </P>
     *
     * @return The queue size for incoming messages
     */
    public int getIncomingMessageQueueSize() {
        return this.incomingMessageQueueSize;
    }

    /**
     * Sets the maximum period in seconds between communications with a connected {@link Broker}. If no other
     * messages are being exchanged, this controls the rate at which the client will send ping messages to the
     * {@link Broker}.
     * <P>
     * Defaults to {@code 1800} seconds (30 minutes)
     * </P>
     *
     * @param keepAliveInterval The maximum period in seconds between communications with a connected {@link Broker}.
     */
    public void setKeepAliveInterval(int keepAliveInterval) throws IllegalArgumentException {
        if (keepAliveInterval < 0) {
            throw new IllegalArgumentException();
        }
        this.keepAliveInterval = keepAliveInterval;
    }

    /**
     * Returns the maximum period in seconds between communications with a connected {@link Broker}. If no other
     * messages are being exchanged, this controls the rate at which the client will send ping messages to the
     * {@link Broker}.
     * <P>
     * Defaults to {@code 1800} seconds (30 minutes)
     * </P>
     *
     * @return The maximum period in seconds between communications with a connected {@link Broker}.
     */
    public int getKeepAliveInterval() {
        return keepAliveInterval;
    }

    /**
     * Sets the maximum number of connection attempts for each {@link Broker} specified.
     * <P>
     * A value of {@code -1} indicates that the client will continue to retry without limit until it establishes
     * a connection.
     * </P>
     * <P>
     * Defaults to {@code -1}
     * </P>
     *
     * @param retries The maximum number of connection attempts for each {@link Broker} specified.
     */
    public void setConnectRetries(final int retries) {
        this.connectRetries = retries;
    }

    /**
     * Returns the maximum number of connection attempts for each {@link Broker} specified.
     * <P>
     * A value of {@code -1} indicates that the client will continue to retry without limit until it establishes
     * a connection.
     * </P>
     * <P>
     * Defaults to {@code -1}
     * </P>
     *
     * @return The maximum number of connection attempts for each {@link Broker} specified.
     */
    public int getConnectRetries() {
        return this.connectRetries;
    }

    /**
     * Sets whether the client should infinitely retry to reconnect when it gets disconnected
     * <P>
     * Defaults to {@code true}
     * </P>
     *
     * @param infiniteReconnect Whether the client should infinitely retry to reconnect when
     *                          it gets disconnected
     */
    public void setInfiniteReconnectRetries(boolean infiniteReconnect) {
        this.infiniteReconnect = infiniteReconnect;
    }

    /**
     * Returns whether the client should infinitely retry to reconnect when it gets disconnected
     * <P>
     * Defaults to {@code true}
     * </P>
     *
     * @return Whether the client should infinitely retry to reconnect when it gets disconnected
     */
    public boolean isInfiniteReconnectRetries() {
        return this.infiniteReconnect;
    }

    /**
     * Returns whether the client should do SSL host name verification when connecting to a broker
     *
     * @return Whether the client should do SSL host name verification when connecting to a broker
     */
    public boolean isHttpsHostnameVerificationEnabled() {
        return httpsHostnameVerificationEnabled;
    }

    /**
     * Sets whether the client should do SSL host name verification when connecting to a broker.
     * <P>
     * Defaults to {@code false}
     * </P>
     *
     * @param httpsHostnameVerificationEnabled Whether the client should do SSL host name
     *                                         verification when connecting to a broker.
     */
    public void setHttpsHostnameVerificationEnabled(boolean httpsHostnameVerificationEnabled) {
        this.httpsHostnameVerificationEnabled = httpsHostnameVerificationEnabled;
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
     * Returns the amount of time (in ms) for the first connect retry, defaults to {@code 1000}
     *
     * @return The amount of time (in ms) for the first connect retry, defaults to {@code 1000}
     */
    public int getReconnectDelay() {
        return this.reconnectDelay;
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
    public synchronized long getReconnectDelayMax() {
        return this.reconnectDelayMax;
    }

    /**
     * The exponential reconnect back off multiplier, defaults to {@code 2}
     *
     * @param multiplier The exponential reconnect back off multiplier, defaults to {@code 2}
     */
    public void setReconnectBackOffMultiplier(final int multiplier) {
        this.backOffMultiplier = multiplier;
    }

    /**
     * Returns the exponential reconnect back off multiplier, defaults to {@code 2}
     *
     * @return The exponential reconnect back off multiplier, defaults to {@code 2}
     */
    public int getReconnectBackOffMultiplier() {
        return this.backOffMultiplier;
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
     * Returns the HTTP proxy address
     *
     * @return The HTTP proxy address
     */
    public String getProxyAddress() {
        return proxyAddress;
    }

    /**
     * Sets the HTTP proxy address
     *
     * @param proxyAddress The HTTP proxy address
     */
    public void setProxyAddress(String proxyAddress) {
        this.proxyAddress = proxyAddress;
    }

    /**
     * Returns the HTTP proxy port
     *
     * @return The HTTP proxy port
     */
    public int getProxyPort() {
        return proxyPort;
    }

    /**
     * Sets the HTTP proxy port
     *
     * @param proxyPort The HTTP proxy port
     */
    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    /**
     * Returns the HTTP proxy user name
     *
     * @return The HTTP proxy user name
     */
    public String getProxyUserName() {
        return proxyUserName;
    }

    /**
     * Sets the HTTP proxy user name
     *
     * @param proxyUserName The HTTP proxy user name
     */
    public void setProxyUserName(String proxyUserName) {
        this.proxyUserName = proxyUserName;
    }

    /**
     * Returns the the HTTP proxy password
     *
     * @return The the HTTP proxy password
     */
    public char[] getProxyPassword() {
        return proxyPassword;
    }

    /**
     * Sets the the HTTP proxy password
     *
     * @param proxyPassword The the HTTP proxy password
     */
    public void setProxyPassword(char[] proxyPassword) {
        this.proxyPassword = proxyPassword;
    }

    /**
     * Method to write out the dxlClient.config file from the DXLClientConfig object member variables
     *
     * @param configFile The path to the dxlClient.config file
     * @throws Exception If there is an issue with writing the dxlClient.config file
     */
    public void write(String configFile) throws Exception {
        final String certsSection = "Certs";

        final IniParser parser = new IniParser();
        // Add Use WebSockets
        parser.addValue(GENERAL_INI_SECTION, USE_WEBSOCKETS_INI_KEY_NAME, String.valueOf(this.useWebSockets));

        // Add Broker Cert Chain
        parser.addValue(CERTS_INI_SECTION, BROKER_CERT_INI_CHAIN_KEY_NAME, this.brokerCaBundlePathOriginal);
        // Add Cert File
        parser.addValue(CERTS_INI_SECTION, CERT_FILE_INI_KEY_NAME, this.certFileOriginal);
        // Add Private Key
        parser.addValue(CERTS_INI_SECTION, PRIVATE_KEY_INI_KEY_NAME, this.privateKeyOriginal);

        // Add Brokers
        for (Broker broker : this.brokers) {
            parser.addValue(BROKERS_INI_SECTION, broker.getUniqueId(), broker.toConfigString());
        }

        // Add WebSocket Brokers
        for (Broker broker : this.webSocketBrokers) {
            parser.addValue(WEBSOCKET_BROKERS_INI_SECTION, broker.getUniqueId(), broker.toConfigString());
        }

        // Add Proxy info
        if (StringUtils.isNotBlank(this.proxyAddress)) {
            parser.addValue(PROXY_INI_SECTION, PROXY_ADDRESS, this.proxyAddress);
            parser.addValue(PROXY_INI_SECTION, PROXY_PORT, String.valueOf(this.proxyPort));
            parser.addValue(PROXY_INI_SECTION, PROXY_USER_NAME, this.proxyUserName);
            parser.addValue(PROXY_INI_SECTION, PROXY_USER_PASSWORD, String.valueOf(this.proxyPassword));
        }

        parser.write(configFile);
    }

    /**
     * Returns the randomness delay percentage (between {@code 0.0} and {@code 1.0}). When
     * calculating the reconnect delay, this percentage indicates how much randomness there
     * should be in the current delay. For example, if the current delay is 100ms, a value of
     * .25 would mean that the actual delay would be between 100ms and 125ms. The default value
     * is {@code 0.25}
     *
     * @return The randomness delay percentage (between {@code 0.0} and {@code 1.0}
     */
    public float getReconnectDelayRandom() {
        return this.delayRandom;
    }

    /**
     * Returns the sorted and cleaned map of broker to its URI. NOTE: The returned map contains clones of
     * {@link Broker} objects
     *
     * @return The sorted and cleaned map of broker to its URI
     * @throws DxlException If a DXL exception occurs
     */
    protected synchronized Map<String, Broker> getSortedBrokerMap() throws DxlException {
        List<Broker> brokerList = getInUseBrokerList();
        if (brokerList == null || brokerList.isEmpty()) {
            throw new DxlException("No broker defined");
        }

        // The sorted broker map (what will be returned)
        final LinkedHashMap<String, Broker> sortedBrokerMap = new LinkedHashMap<>();

        //
        // Build a map containing the brokers by GUID
        //
        final Map<String, Broker> brokersByGuid = new HashMap<>();
        for (final Broker broker : brokerList) {
            brokersByGuid.put(broker.getUniqueId(), broker);
        }

        final Collection<Broker> remainingBrokers = cloneBrokers(brokersByGuid.values());
        try {
            // Sort the remaining brokers
            addSortedBrokersToMap(getSortedBrokerList(remainingBrokers), sortedBrokerMap);
        } catch (final Exception ex) {
            logger.error("Error attempting to sort brokers", ex);
        }

        // Add the remaining brokers (unsorted)
        remainingBrokers.removeAll(sortedBrokerMap.values());
        addUnsortedBrokersToMap(remainingBrokers, sortedBrokerMap);

        return sortedBrokerMap;
    }

    /**
     * Returns the {@link Broker} list sorted by response time low to high
     *
     * @param brokers The brokers to evaluate
     * @return The {@link Broker} list sorted by response time low to high
     * @throws InterruptedException If the current thread was interrupted while waiting
     * @throws ExecutionException If there is an error checking a socket connection to a broker
     */
    protected synchronized List<Broker> getSortedBrokerList(final Collection<Broker> brokers)
        throws InterruptedException, ExecutionException {
        final List<Future<Broker>> futures = new ArrayList<>();
        final ExecutorService es = Executors.newFixedThreadPool(20);
        try {
            for (final Broker broker : brokers) {
                futures.add(connectToBroker(es, broker, brokerPingTimeout));
            }
        } finally {
            es.shutdown();
            if (!es.awaitTermination(brokerPingTimeout * 5, TimeUnit.MILLISECONDS)) {
                es.shutdownNow();
                if (!es.awaitTermination(brokerPingTimeout * 5, TimeUnit.MILLISECONDS)) {
                    logger.error("Error shutting down getSortedBrokerList executor service thread pool");
                }
            }
        }

        final List<Broker> brokersSorted = new ArrayList<>();
        for (final Future<Broker> f : futures) {
            brokersSorted.add(f.get());
        }
        Collections.sort(brokersSorted);

        return brokersSorted;
    }

    /**
     * Sets the time to wait for an underlying protocol operation to complete. The default value is
     * {@code 2} minutes.
     *
     * @param timeToWait The time to wait for an underlying protocol operation to complete (in milliseconds)
     */
    public synchronized void setOperationTimeToWait(final long timeToWait) {
        this.operationTimeToWait = timeToWait;
    }

    /**
     * Returns the time to wait for an underlying protocol operation to complete. The default value is
     * {@code 2} minutes.
     *
     * @return  The time to wait for an underlying protocol operation to complete (in milliseconds)
     */
    public synchronized long getOperationTimeToWait() {
        return this.operationTimeToWait;
    }

    /**
     * Sets the connection timeout. Defaults to {@code 30} seconds.
     *
     * @param timeout The connection timeout (in seconds)
     */
    public void setConnectTimeout(final int timeout) {
        this.connectTimeout = timeout;
    }

    /**
     * Returns the connection timeout. Defaults to {@code 30} seconds.
     *
     * @return The connection timeout (in seconds)
     */
    public int getConnectTimeout() {
        return this.connectTimeout;
    }

    /**
     * Sets the disconnection timeout. Defaults to {@code 60} seconds.
     *
     * @param timeout The disconnection timeout (in seconds)
     */
    public void setDisconnectTimeout(final int timeout) {
        this.disconnectTimeout = timeout;
    }

    /**
     * Returns the disconnection timeout. Defaults to {@code 60} seconds.
     *
     * @return The disconnection timeout (in seconds)
     */
    public int getDisconnectTimeout() {
        return this.disconnectTimeout;
    }

    /**
     * Clones the specified collection of brokers
     *
     * @param brokers The brokers to clone
     * @return The cloned brokers as a {@link List}
     */
    protected static List<Broker> cloneBrokers(final Collection<Broker> brokers) {
        final List<Broker> clonedBrokers = new ArrayList<>();
        if (brokers != null) {
            for (final Broker broker : brokers) {
                try {
                    clonedBrokers.add(broker.clone());
                } catch (final Exception ex) {
                    logger.error("Error cloning broker", ex);
                }
            }
        }
        return clonedBrokers;
    }

    /**
     * Adds the specified list of unsorted brokers to the "ordered" map. This is used
     * when we are unable to determine a sort order for the brokers. When this occurs,
     * we need to have the map contain both versions of the broker URI (IP and hostname).
     *
     * @param unsortedList The unsorted broker list
     * @param sortedMap    The sorted map to add the unsorted brokers to
     */
    protected static void addUnsortedBrokersToMap(
        final Collection<Broker> unsortedList, final LinkedHashMap<String, Broker> sortedMap) {
        if (unsortedList != null) {
            for (final Broker broker : unsortedList) {
                sortedMap.put(broker.toServerURI(), broker);
                sortedMap.put(broker.toAlternativeServerURI(), broker);
            }
        }
    }

    /**
     * Adds the specified list of sorted brokers to the "ordered" map. This is used
     * when we are able to determine a sort order for the brokers.
     *
     * @param sortedList The sorted broker list
     * @param sortedMap  The sorted map to add the sorted brokers to
     */
    protected static void addSortedBrokersToMap(final List<Broker> sortedList,
                                              final LinkedHashMap<String, Broker> sortedMap) {
        if (sortedList != null) {
            for (final Broker broker : sortedList) {
                if (broker.getResponseTime() != null) {
                    if (broker.isResponseFromIpAddress()) {
                        sortedMap.put(broker.toAlternativeServerURI(), broker);
                    } else {
                        sortedMap.put(broker.toServerURI(), broker);
                    }
                }
            }
        }
    }

    /**
     * Performs a socket connect to the specified broker
     *
     * @param broker      The broker
     * @param useHostname Whether we should use the hostname (or IP address)
     * @param timeout     The timeout for the connection attempt
     * @throws Exception If an error occurs
     */
    private static void socketConnectToBroker(final Broker broker, final boolean useHostname, final int timeout)
        throws Exception {
        try (Socket socket = new Socket()) {
            final InetSocketAddress address =
                new InetSocketAddress(
                    (useHostname ? broker.getHostName() : broker.getIpAddress()), broker.getPort());
            final long startTime = System.nanoTime();
            socket.connect(address, timeout);
            broker.setResponseTime(System.nanoTime() - startTime);
            broker.setResponseFromIpAddress(!useHostname);
        }
    }

    /**
     * Attempts to connect to the specified broker
     *
     * @param es      The executor
     * @param broker  The broker to connect to
     * @param timeout The timeout for the connect attempt
     * @return The future associated with the connect attempt
     */
    protected static Future<Broker> connectToBroker(final ExecutorService es, final Broker broker, final int timeout) {
        return es.submit(
            () -> {
                final Broker result = broker.clone();
                result.setResponseTime(null);
                try {
                    socketConnectToBroker(result, true, timeout);
                } catch (final Exception ex) {
                    if (broker.getIpAddress() != null && !broker.getIpAddress().isEmpty()) {
                        try {
                            socketConnectToBroker(result, false, timeout);
                        } catch (final Exception ex2) { /**/ }
                    }
                }
                return result;
            }
        );
    }

    /**
     * Normalizes the location of the specified configuration-related file. First, the path specified
     * is checked. If that file does not exist, a path relative to the specified root configuration file
     * is checked.
     *
     * @param   configFile The root configuration file
     * @param   name The name of the configuration-related file
     * @return  The location of the configuration-related file
     * @throws  DxlException If an error occurs
     */
    private static String normalizeConfigFile(final String configFile, final String name) throws DxlException {
        File f = new File(name);
        if (!f.exists()) {
            f = new File(new File(configFile).getParent(), name);
            if (!f.exists()) {
                throw new DxlException("File not found: " + name);
            }
        }
        return f.getPath();
    }

    /**
     * This method converts a Brokers or BrokersWebSockets section of a configuration file in to a list of
     * {@link Broker} objects.
     *
     * @param configSection A map representing the keys and values of a Brokers or BrokersWebSockets section of a
     * configuration file.
     * @param useWebSockets Whether to use WebSockets or regular MQTT over tcp when connecting to a broker
     * @return A list of {@link Broker} objects representing the Brokers or BrokersWebSockets section of a
     * configuration file.
     * @throws DxlException If an error occurs
     */
    private static List<Broker> getBrokerListFromConfigSection(Map<String, String> configSection,
                                                               boolean useWebSockets) throws DxlException {
        List<Broker> brokers = new ArrayList<>();
        for (Map.Entry<String, String> entry : configSection.entrySet()) {
            brokers.add(Broker.parse(entry.getValue(), useWebSockets));
        }

        return brokers;
    }

    /**
     * This method allows creation of a {@link DxlClientConfig} object from a specified configuration file. The
     * information contained in the file has a one-to-one correspondence with the {@link DxlClientConfig} constructor.
     *
     * <pre>
     * [General]
     * UseWebSockets=false
     *
     * [Certs]
     * BrokerCertChain=c:\\certs\\brokercerts.crt
     * CertFile=c:\\certs\\client.crt
     * PrivateKey=c:\\certs\\client.key
     *
     * [Brokers]
     * mybroker=mybroker;8883;mybroker.mcafee.com;192.168.1.12
     * mybroker2=mybroker2;8883;mybroker2.mcafee.com;192.168.1.13
     *
     * [BrokersWebSockets]
     * mybroker=mybroker;8883;mybroker.mcafee.com;192.168.1.12
     * mybroker2=mybroker2;8883;mybroker2.mcafee.com;192.168.1.13
     *
     * [Proxy]
     * Address=proxy.mycompany.com
     * Port=3128
     * User=proxyUser
     * Password=proxyPassword
     *
     * </pre>
     * The configuration file can be loaded as follows:
     * <pre>
     * DxlClientConfig.createDxlConfigFromFile("c:\\certs\\dxlclient.cfg");
     * </pre>
     *
     * @param fileName Path to the configuration file
     * @return A {@link DxlClientConfig} object corresponding to the specified configuration file
     * @throws DxlException If an error occurs
     */
    public static DxlClientConfig createDxlConfigFromFile(final String fileName) throws DxlException {
        try {
            final IniParser parser = new IniParser();
            parser.read(fileName);

            String brokerCaBundleFileOriginal = parser.getValue(CERTS_INI_SECTION, BROKER_CERT_INI_CHAIN_KEY_NAME, "");
            String certFileOriginal = parser.getValue(CERTS_INI_SECTION, CERT_FILE_INI_KEY_NAME, "");
            String privateKeyOriginal = parser.getValue(CERTS_INI_SECTION, PRIVATE_KEY_INI_KEY_NAME,  "");

            String brokerCaBundlePath =
                    normalizeConfigFile(fileName, brokerCaBundleFileOriginal);
            String certFile =
                    normalizeConfigFile(fileName, certFileOriginal);
            String privateKey =
                    normalizeConfigFile(fileName, privateKeyOriginal);
            Map<String, String> brokersSection = Collections.EMPTY_MAP;
            try {
                brokersSection = parser.getSection(BROKERS_INI_SECTION);
            } catch (Exception ex) {
                // The Brokers section was not found in the config file
            }

            Map<String, String> webSocketBrokersSection = Collections.EMPTY_MAP;
            try {
                webSocketBrokersSection = parser.getSection(WEBSOCKET_BROKERS_INI_SECTION);
            } catch (Exception ex) {
                // The BrokersWebSockets section was not found in the config file
            }

            // Get the list of MQTT brokers
            List<Broker> brokers = getBrokerListFromConfigSection(brokersSection, false);
            // Get the list of WebSocket brokers
            List<Broker> webSocketBrokers = getBrokerListFromConfigSection(webSocketBrokersSection, true);

            if (brokers.size() == 0 && webSocketBrokers.size() == 0) {
                throw new DxlException("No brokers are specified");
            }

            final DxlClientConfig dxlClientConfig = new DxlClientConfig(brokerCaBundlePath, certFile,
                    privateKey, brokers, webSocketBrokers);
            //set the original paths
            dxlClientConfig.brokerCaBundlePathOriginal = brokerCaBundleFileOriginal;
            dxlClientConfig.certFileOriginal = certFileOriginal;
            dxlClientConfig.privateKeyOriginal = privateKeyOriginal;
            dxlClientConfig.useWebSockets = stringToBooleanMap.get(parser.getValue(GENERAL_INI_SECTION,
                USE_WEBSOCKETS_INI_KEY_NAME, (!webSocketBrokers.isEmpty() && brokers.isEmpty()) ? "true" : "false"));

            // Get the proxy information
            dxlClientConfig.proxyAddress = parser.getValue(PROXY_INI_SECTION, PROXY_ADDRESS, "");
            dxlClientConfig.proxyPort = Integer.parseInt(
                parser.getValue(PROXY_INI_SECTION, PROXY_PORT, "0"));
            dxlClientConfig.proxyUserName = parser.getValue(PROXY_INI_SECTION, PROXY_USER_NAME, "");
            dxlClientConfig.proxyPassword =
                parser.getValue(PROXY_INI_SECTION, PROXY_USER_PASSWORD, "").toCharArray();

            return dxlClientConfig;
        } catch (Exception ex) {
            throw new DxlException("Error reading configuration file: " + fileName, ex);
        }
    }
}
