/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client;

import com.opendxl.client.exception.DxlException;
import com.opendxl.client.ssl.KeyStoreUtils;
import com.opendxl.client.util.IniParser;
import com.opendxl.client.util.UuidGenerator;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * The {@link DxlClientConfig} class holds the information necessary to connect a {@link DxlClient} to the DXL fabric.
 */
public class DxlClientConfig {

    /**
     *  The default keystore password
     */
    public static final String KS_PASS = "";

    /**
     * The unique identifier of the client
     */
    private String uniqueId;

    /**
     * The list of brokers
     */
    private List<Broker> brokers;

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
     * The broker connection timeout (in ms), defaults to 500ms
     */
    private int brokerConnectTimeout =
        Integer.parseInt(System.getProperty(Constants.SYSPROP_CONNECT_TIMEOUT, "500"));

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
     * Constructs the configuration
     *
     * @param brokerCaBundlePath The file name of a bundle containing the broker CA certificates in PEM format
     * @param certFile The file name of the client certificate in PEM format
     * @param privateKey The file name of the client private key in PEM format
     * @param brokers A list of {@link Broker} objects representing brokers comprising the DXL fabric.
     *                When invoking the {@link DxlClient#connect} method, the {@link DxlClient} will attempt to connect
     *                to the closest broker.
     */
    public DxlClientConfig(
        final String brokerCaBundlePath, final String certFile, final String privateKey,
        final List<Broker> brokers) {
        this.uniqueId = UuidGenerator.generateIdAsString();
        this.brokers = brokers;
        this.brokerCaBundlePath = brokerCaBundlePath;
        this.certFile = certFile;
        this.privateKey = privateKey;
    }

    /**
     * Returns the {@link KeyStore} associated with the client configuration
     *
     * @return The {@link KeyStore} associated with the client configuration
     * @throws DxlException If an error occurs
     */
    public synchronized KeyStore getKeyStore() throws DxlException {
        if (this.keystore == null) {
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
     * Returns the unique identifier of the client instance
     *
     * @return The unique identifier of the client instance
     */
    public String getUniqueId() {
        return uniqueId;
    }

    /**
     * Returns the list of {@link Broker} objects representing brokers comprising the DXL fabric.
     * When invoking the {@link DxlClient#connect} method, the {@link DxlClient} will attempt to connect to the closest
     * broker.
     *
     * @return The list of {@link Broker} objects representing brokers comprising the DXL fabric.
     */
    public List<Broker> getBrokerList() {
        return brokers;
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
    synchronized Map<String, Broker> getSortedBrokerMap() throws DxlException {
        if (brokers == null || brokers.isEmpty()) {
            throw new DxlException("No broker defined");
        }

        // The sorted broker map (what will be returned)
        final LinkedHashMap<String, Broker> sortedBrokerMap = new LinkedHashMap<>();

        //
        // Build a map containing the brokers by GUID
        //
        final Map<String, Broker> brokersByGuid = new HashMap<>();
        for (final Broker broker : brokers) {
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
     */
    synchronized List<Broker> getSortedBrokerList(final Collection<Broker> brokers)
        throws InterruptedException, ExecutionException {
        final List<Future<Broker>> futures = new ArrayList<>();
        final ExecutorService es = Executors.newFixedThreadPool(20);
        try {
            for (final Broker broker : brokers) {
                futures.add(connectToBroker(es, broker, brokerConnectTimeout));
            }
        } finally {
            es.shutdown();
        }

        final List<Broker> brokersSorted = new ArrayList<>();
        for (final Future<Broker> f : futures) {
            brokersSorted.add(f.get());
        }
        Collections.sort(brokersSorted);

        return brokersSorted;
    }

    /**
     * Clones the specified collection of brokers
     *
     * @param brokers The brokers to clone
     * @return The cloned brokers as a {@link List}
     */
    private static List<Broker> cloneBrokers(final Collection<Broker> brokers) {
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
    private static void addUnsortedBrokersToMap(
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
    private static void addSortedBrokersToMap(final List<Broker> sortedList,
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
    private static Future<Broker> connectToBroker(final ExecutorService es, final Broker broker, final int timeout) {
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
     * This method allows creation of a {@link DxlClientConfig} object from a specified configuration file. The
     * information contained in the file has a one-to-one correspondence with the {@link DxlClientConfig} constructor.
     *
     * <pre>
     * [Certs]
     * BrokerCertChain=c:\\certs\\brokercerts.crt
     * CertFile=c:\\certs\\client.crt
     * PrivateKey=c:\\certs\\client.key
     *
     * [Brokers]
     * mybroker=mybroker;8883;mybroker.mcafee.com;192.168.1.12
     * mybroker2=mybroker2;8883;mybroker2.mcafee.com;192.168.1.13
     * </pre>
     * The configuration file can be loaded as follows:
     * <pre>
     * DxlClientConfig.createDxlConfigFromFile("c:\\certs\\dxlclient.cfg");
     * </pre>
     *
     * @param fileName Path to the configuration file
     * @return A {@link }DxlClientConfig} object corresponding to the specified configuration file
     * @throws DxlException If an error occurs
     */
    public static DxlClientConfig createDxlConfigFromFile(final String fileName) throws DxlException {
        try {
            final String certsSection = "Certs";

            final IniParser parser = new IniParser();
            parser.read(fileName);

            String brokerCaBundlePath =
                normalizeConfigFile(fileName, parser.getValue(certsSection, "BrokerCertChain"));
            String certFile =
                normalizeConfigFile(fileName, parser.getValue(certsSection, "CertFile"));
            String privateKey =
                normalizeConfigFile(fileName, parser.getValue(certsSection, "PrivateKey"));
            Map<String, String> brokersSection = parser.getSection("Brokers");

            List<Broker> brokers = new ArrayList<>();
            for (Map.Entry<String, String> entry : brokersSection.entrySet()) {
                final String[] values = entry.getValue().split(";");
                if (values.length < 3) {
                    throw new DxlException("Invalid broker specification: " + entry.getValue());
                }
                final String id = entry.getKey();
                final String portStr = values[1];
                final String host = values[2];

                String ipAddress = host;
                if (values.length > 3) {
                    ipAddress = values[3];
                }
                int port;
                try {
                    port = Integer.parseInt(portStr);
                } catch (NumberFormatException ex) {
                    throw new DxlException("Port specified is not valid: " + portStr);
                }
                brokers.add(new Broker(id, port, host, ipAddress));
            }

            if (brokers.size() == 0) {
                throw new DxlException("No brokers are specified");
            }

            return new DxlClientConfig(brokerCaBundlePath, certFile, privateKey, brokers);
        } catch (Exception ex) {
            throw new DxlException("Error reading configuration file: " + fileName, ex);
        }
    }
}
