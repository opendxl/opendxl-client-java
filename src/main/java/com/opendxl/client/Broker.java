/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.opendxl.client.exception.MalformedBrokerException;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * The class {@link Broker} represents a DXL message broker.
 * <P>
 * Instances of this class are created for the purpose of connecting to the DXL fabric.
 * </P>
 * <P>
 * There are a couple of ways to create Broker instances:
 * </P>
 * <UL>
 * <LI>Invoking the {@link Broker} constructor directly</LI>
 * <LI>When creating a {@link DxlClientConfig} object via the {@link DxlClientConfig#createDxlConfigFromFile}
 * static method</LI>
 * </UL>
 */
public class Broker implements Comparable<Broker>, Cloneable {

    /**
     * Constant for ssl protocol
     */
    public static final String SSL_PROTOCOL = "ssl";

    /**
     * The WSS protocol identifier
     */
    public static final String WSS_PROTOCOL = "wss";

    /** Constant for parse separator */
    public static final String FIELD_SEPARATOR = ";";

    /**
     * The unique identifier of the Broker
     */
    @JsonAlias({"guid", "uniqueId"})
    private String uniqueId = "";

    /**
     * The host name or IP address of the broker
     */
    private String hostName = "";

    /**
     * The IP address of the broker (optional)
     */
    private String ipAddress = null;

    /**
     * The protocol used by the broker (wss or ssl). The default protocol is ssl.
     */
    private String protocol = SSL_PROTOCOL;

    /**
     * The TCP port of the broker
     */
    private int port = 8883;

    /**
     * The broker response time in nano seconds, or {@code null} if it was not checked
     */
    private Long responseTime = null;

    /**
     * The broker response came from the secondary check using the IP address
     */
    private boolean responseFromIpAddress = false;

    /**
     * Constructor for the {@link Broker}
     */
    protected Broker() { }

    /**
     * Constructor for the {@link Broker}
     *
     * @param uniqueId A unique identifier for the broker, used to identify the broker in log messages, etc.
     * @param port The port of the broker
     * @param hostName The host name or IP address of the broker (required)
     * @param ipAddress A valid IP address for the broker. This allows for both the host name and IP address to be
     *                  used when connecting to the broker (optional).
     */
    public Broker(final String uniqueId, final int port, final String hostName, final String ipAddress) {
        this(uniqueId, port, hostName, ipAddress, false);
    }

    /**
     * Constructor for the {@link Broker}
     *
     * @param uniqueId A unique identifier for the broker, used to identify the broker in log messages, etc.
     * @param port The port of the broker
     * @param hostName The host name or IP address of the broker (required)
     * @param ipAddress A valid IP address for the broker. This allows for both the host name and IP address to be
     *                  used when connecting to the broker (optional).
     * @param useWebSockets Whether to use WebSockets or regular MQTT over tcp when connecting to a broker
     */
    public Broker(final String uniqueId, final int port, final String hostName, final String ipAddress,
                  final boolean useWebSockets) {
        this.uniqueId = uniqueId;
        this.hostName = hostName;
        this.ipAddress = ipAddress;
        this.port = port;
        this.protocol = useWebSockets ? WSS_PROTOCOL : SSL_PROTOCOL;
    }

    /**
     * Creates a clone of the {@link Broker}
     *
     * @return The cloned {@link Broker}
     */
    @Override
    public Broker clone() throws CloneNotSupportedException {
        super.clone();
        Broker broker = new Broker();
        broker.uniqueId = this.uniqueId;
        broker.hostName = this.hostName;
        broker.ipAddress = this.ipAddress;
        broker.port = this.port;
        broker.responseTime = this.responseTime;
        broker.responseFromIpAddress = this.responseFromIpAddress;
        broker.protocol = this.protocol;
        return broker;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toServerURI();
    }

    /**
     * Outputs the broker in the "config" string format:
     * <P>
     * {@code [UniqueId];[Port];[HostName];[IpAddress]}
     * </P>
     *
     * @return Outputs the broker in the "config" string format
     */
    String toConfigString() {
        StringBuilder sb = new StringBuilder();
        sb.append(uniqueId).append(FIELD_SEPARATOR);
        sb.append(port).append(FIELD_SEPARATOR);
        sb.append(hostName).append(FIELD_SEPARATOR);
        sb.append(ipAddress);

        return sb.toString();
    }

    /**
     * Returns a URI representation of the broker in the format {@code [Protocol]://[ServerName]:[Port]}
     *
     * @return A URI representation of the broker
     */
    String toServerURI() {
        StringBuilder sb = new StringBuilder();
        sb.append(protocol);
        sb.append("://");
        String serverName = (BrokerHostNameHelper.isValidIPv6Address(hostName)
            ? "[" + hostName + "]"
            : hostName);
        sb.append(serverName);
        sb.append(":");
        sb.append(port);
        return sb.toString();
    }

    /**
     * Returns an alternative URI representation of the broker using its IP address in the format
     * {@code [Protocol]://[ServerName]:[Port]}
     *
     * @return An alternative URI representation of the broker using its IP address
     */
    String toAlternativeServerURI() {
        StringBuilder sb = new StringBuilder();
        sb.append(protocol);
        sb.append("://");
        String serverName;
        if (ipAddress != null && !ipAddress.isEmpty()) {
            serverName = (BrokerHostNameHelper.isValidIPv6Address(ipAddress)
                ? "[" + ipAddress + "]"
                : ipAddress);
        } else {
            serverName = (BrokerHostNameHelper.isValidIPv6Address(hostName)
                ? "[" + hostName + "]"
                : hostName);
        }
        sb.append(serverName);
        sb.append(":");
        sb.append(port);
        return sb.toString();
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("NullableProblems")
    @Override
    public int compareTo(Broker that) {
        return ((responseTime == null && that.responseTime == null) ? 0 : (responseTime == null ? 1
            : (that.responseTime == null ? -1 : responseTime.compareTo(that.responseTime))));
    }

    /**
     * Returns a unique identifier for the broker, used to identify the broker in log messages, etc.
     *
     * @return A unique identifier for the broker, used to identify the broker in log messages, etc.
     */
    public String getUniqueId() {
        return uniqueId;
    }

    /**
     * Sets the unique identifier for the broker, used to identify the broker in log messages, etc.
     *
     * @param uniqueId The unique identifier for the broker, used to identify the broker in log messages, etc.
     */
    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    /**
     * Returns the host name or IP address of the broker
     *
     * @return The host name or IP address of the broker
     */
    public String getHostName() {
        return hostName;
    }

    /**
     * Sets the host name or IP address of the broker
     *
     * @param hostName The host name or IP address of the broker
     */
    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    /**
     * Returns a valid IP address for the broker. This allows for both the host name and IP address to be used when
     * connecting to the broker.
     *
     * @return A valid IP address for the broker
     */
    public String getIpAddress() {
        return ipAddress;
    }

    /**
     * Sets the IP address for the broker
     *
     * @param ipAddress The IP address for the broker
     */
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    /**
     * Returns the connection protocol of the broker.
     *
     * @return The connection protocol of the broker
     */
    public String getProtocol() {
        return protocol;
    }

    /**
     * Set the connection protocol of the broker.
     *
     * @param protocol The connection protocol of the broker (wss or ssl).
     */
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    /**
     * Returns the port of the broker
     *
     * @return The port of the broker
     */
    public int getPort() {
        return port;
    }

    /**
     * Set the port of the broker
     *
     * @param port The port of the broker
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Sets the response time for the broker (essentially the ping time)
     *
     * @param responseTime The response time for the broker (essentially the ping time)
     */
    void setResponseTime(Long responseTime) {
        this.responseTime = responseTime;
    }

    /**
     * Returns the response time for the broker (essentially the ping time)
     *
     * @return The response time for the broker (essentially the ping time)
     */
    public Long getResponseTime() {
        return this.responseTime;
    }

    /**
     * Sets whether the response time is for the broker's IP address
     *
     * @param responseFromIpAddress Whether the response time is for the broker's IP address
     */
    void setResponseFromIpAddress(boolean responseFromIpAddress) {
        this.responseFromIpAddress = responseFromIpAddress;
    }

    /**
     * Returns whether the response time is for the broker's IP address
     *
     * @return Whether the response time is for the broker's IP address
     */
    public boolean isResponseFromIpAddress() {
        return responseFromIpAddress;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Broker)) {
            return false;
        }

        Broker that = (Broker) obj;

        if (uniqueId != null
            ? !uniqueId.equals(that.uniqueId)
            : that.uniqueId != null) {
            return false;
        }
        if (hostName != null
            ? !hostName.equals(that.hostName)
            : that.hostName != null) {
            return false;
        }
        if (ipAddress != null
            ? !ipAddress.equals(that.ipAddress)
            : that.ipAddress != null) {
            return false;
        }
        return port == that.port;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        int result = uniqueId != null
            ? uniqueId.hashCode()
            : 0;
        result = 31 * result + (hostName != null
            ? hostName.hashCode()
            : 0);
        result = 31 * result + (ipAddress != null
            ? ipAddress.hashCode()
            : 0);
        result = 31 * result + (port);
        return result;
    }

    /**
     * Constructs and returns a {@link Broker} for the specified configuration string of the following format:
     * <P>
     * {@code [UniqueId];[Port];[HostName];[IpAddress]}
     * </P>
     * @param configString The configuration string
     * @return A {@link Broker} corresponding to the specified configuration string
     * @throws MalformedBrokerException If the format is malformed
     */
    public static Broker parse(final String configString)
        throws MalformedBrokerException {
        // [UniqueId];[Port];[HostName];[IpAddress]
        return parse(configString, false);
    }

    /**
     * Constructs and returns a {@link Broker} for the specified protocol and configuration string of the
     * following format:
     * <P>
     * {@code [UniqueId];[Port];[HostName];[IpAddress]}
     * </P>
     * @param configString The configuration string
     * @param protocol The protocol to use when connecting to a broker
     * @return A {@link Broker} corresponding to the specified configuration string and protocol
     * @throws MalformedBrokerException If the format is malformed
     */
    public static Broker parse(final String configString, final String protocol) throws MalformedBrokerException {
        return parse(configString, WSS_PROTOCOL.equals(protocol));
    }

    /**
     * Constructs and returns a {@link Broker} for the specified configuration string of the following format:
     * <P>
     * {@code [UniqueId];[Port];[HostName];[IpAddress]}
     * </P>
     * @param configString The configuration string
     * @param useWebSockets Whether to use WebSockets or regular MQTT over tcp when connecting to a broker
     * @return A {@link Broker} corresponding to the specified configuration string
     * @throws MalformedBrokerException If the format is malformed
     */
    public static Broker parse(final String configString, boolean useWebSockets)
        throws MalformedBrokerException {
        // [UniqueId];[Port];[HostName];[IpAddress]
        if (StringUtils.isBlank(configString)) {
            throw new MalformedBrokerException("Missing argument for creating a broker object");
        }

        final Broker broker = new Broker();
        List<String> elements = Arrays.asList(configString.split(FIELD_SEPARATOR));
        if (elements.size() < 3) {
            throw new MalformedBrokerException("Missing element(s) for creating a broker object");
        }

        broker.uniqueId = elements.get(0);
        // Remove brackets around IPv6 address
        broker.hostName = elements.get(2).replaceAll("[\\[\\]]", "");

        // Validate the host name
        if (!BrokerHostNameHelper.isValidHostNameOrIPAddress(broker.hostName)) {
            throw new MalformedBrokerException("Invalid hostname");
        }

        if (elements.size() > 3) {
            // Remove brackets around IPv6 address
            broker.ipAddress = elements.get(3).replaceAll("[\\[\\]]", "");

            // Validate the IP address
            if (!BrokerHostNameHelper.isValidHostNameOrIPAddress(broker.ipAddress)) {
                throw new MalformedBrokerException("Invalid IP address or host name");
            }
        }

        try {
            broker.port = Integer.parseInt(elements.get(1));
            if (broker.port < 1 || broker.port > 65535) {
                throw new MalformedBrokerException("Invalid port");
            }
        } catch (NumberFormatException ex) {
            throw new MalformedBrokerException("Invalid port");
        }

        broker.protocol = useWebSockets ? WSS_PROTOCOL : SSL_PROTOCOL;
        return broker;
    }
}

