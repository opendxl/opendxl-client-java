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
 * The Data Exchange Layer (DXL) client configuration
 */
public class DxlClientConfig {
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

    private String brokerCaBundlePath;
    private String certFile;
    private String privateKey;

    /**
     * The broker connection timeout (in ms), default 500ms
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
     * The logger
     */
    private static Logger logger = Logger.getLogger(DxlClientConfig.class);

    /**
     * Incoming message queue size
     */
    private int incomingMessageQueueSize =
        Integer.parseInt(
            System.getProperty(Constants.SYSPROP_INCOMING_MESSAGE_QUEUE_SIZE, Integer.toString(16384)));

    public DxlClientConfig(
        final String brokerCaBundlePath, final String certFile, final String privateKey,
        final List<Broker> brokers) {
        this.uniqueId = UuidGenerator.generateIdAsString();
        this.brokers = brokers;
        this.brokerCaBundlePath = brokerCaBundlePath;
        this.certFile = certFile;
        this.privateKey = privateKey;
    }

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
     * Returns the Broker list.
     *
     * @return The broker list.
     */
    public List<Broker> getBrokerList() {
        return brokers;
    }

    /**
     * Sets the thread pool size for incoming MQTT messages
     *
     * @param size The thread pool size for incoming MQTT messages
     */
    public void setIncomingMessageThreadPoolSize(final int size) {
        this.incomingMessageThreadPoolSize = size;
    }

    /**
     * Returns the thread pool size for incoming MQTT messages
     *
     * @return The thread pool size for incoming MQTT messages
     */
    public int getIncomingMessageThreadPoolSize() {
        return this.incomingMessageThreadPoolSize;
    }

    /**
     * Sets the queue size for incoming MQTT messages (will block when queue is full)
     *
     * @param size The queue size for incoming MQTT messages
     */
    public void setIncomingMessageQueueSize(final int size) {
        this.incomingMessageQueueSize = size;
    }

    /**
     * Returns the queue size for incoming MQTT messages (will block when queue is full)
     *
     * @return The queue size for incoming MQTT messages
     */
    public int getIncomingMessageQueueSize() {
        return this.incomingMessageQueueSize;
    }

    /**
     * Returns the "keep alive" interval.
     *
     * @return the keep alive interval in seconds
     */
    public int getKeepAliveInterval() {
        return keepAliveInterval;
    }

    /**
     * Sets the "keep alive" interval.
     *
     * @param keepAliveInterval the interval, measured in seconds, must be &gt;= 0.
     */
    public void setKeepAliveInterval(int keepAliveInterval) throws IllegalArgumentException {
        if (keepAliveInterval < 0) {
            throw new IllegalArgumentException();
        }
        this.keepAliveInterval = keepAliveInterval;
    }

    /**
     * Returns the sorted and cleaned map of broker to its URI
     * Note, the returned map contains clones of Broker objects
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
     * Returns the Broker list sorted by response time low to high
     *
     * @param brokers The brokers to evaluate
     * @return The Broker list sorted by response time low to high
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
     * @return The cloned brokers as a {@link java.util.List}
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
                int port = 8883;
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
