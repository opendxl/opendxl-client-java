/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client;

import com.opendxl.client.exception.MalformedBrokerException;
import com.opendxl.client.util.ServerNameHelper;
import com.opendxl.client.util.UuidGenerator;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * The definition of a DXL broker
 */
public class Broker implements Comparable<Broker>, Cloneable {
    /**
     * Constant for ssl protocol
     */
    public static final String SSL_PROTOCOL = "ssl";

    /** Constant for separator */
    public static final String FIELD_SEPARATOR = ";";

    /**
     * The unique ID of the Broker
     */
    private String uniqueId = "";

    /**
     * The hostName or IP address of the broker
     */
    private String hostName = "";

    /**
     * The IP address of the broker (optional)
     */
    private String ipAddress = null;

    /**
     * The TCP port of the broker
     */
    private int port = 8883;

    /**
     * The broker response time in nano seconds, or null if no response or not tested
     */
    private Long responseTime = null;

    /**
     * The broker response came from the secondary test using the IP address
     */
    private boolean responseFromIpAddress = false;

    /**
     * Constructs a Broker object
     * Default constructor
     */
    public Broker() {
    }

    /**
     * Constructs a Broker object
     *
     * @param uniqueId  The unique ID of the Broker
     * @param port      The TCP port of the broker
     * @param hostName  The hostName or IP address of the broker
     * @param ipAddress The IP address of the broker (optional)
     */
    public Broker(final String uniqueId, final int port, final String hostName, final String ipAddress) {
        this.uniqueId = uniqueId;
        this.hostName = hostName;
        this.ipAddress = ipAddress;
        this.port = port;
    }

    /**
     * Clone a Broker object
     *
     * @return The cloned Broker object
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
        return broker;
    }

    /**
     * Constructs a Broker object for the specified url
     *
     * @param url The connection url
     * @return A Broker object for the specified url
     * @throws MalformedBrokerException If the URL is malformed
     */
    public static Broker parse(final String url)
        throws MalformedBrokerException {
        return parse(url, 8883);
    }

    /**
     * Constructs a Broker object for the specified url and default port
     *
     * @param url             The connection url
     * @param defaultPort     The connection default port
     * @return A Broker object for the specified url and default port
     * @throws MalformedBrokerException If the URL is malformed
     */
    public static Broker parse(final String url, final int defaultPort)
        throws MalformedBrokerException {

        final Broker broker = new Broker();

        String hostname = url;
        int port = defaultPort;

        String[] elements = hostname.split("://");
        if (elements.length == 2) {
            hostname = elements[1];
        }
        elements = hostname.split(":");
        if (elements.length == 2) {
            hostname = elements[0];
            try {
                port = Integer.parseInt(elements[1]);
            } catch (Exception ex) {
                throw new MalformedBrokerException("Error parsing port", ex);
            }
        }

        broker.hostName = hostname.replaceAll("[\\[\\]]", ""); // Remove brackets around IPv6 address
        broker.port = port;
        broker.uniqueId = UuidGenerator.generateIdAsString();

        return broker;
    }

    public static Broker parseFromConfigString(final String configString)
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
        if (!ServerNameHelper.isValidHostNameOrIPAddress(broker.hostName)) {
            throw new MalformedBrokerException("Invalid hostname");
        }

        if (elements.size() > 3) {
            // Remove brackets around IPv6 address
            broker.ipAddress = elements.get(3).replaceAll("[\\[\\]]", "");
            if (!ServerNameHelper.isValidIPAddress(broker.ipAddress)) {
                throw new MalformedBrokerException("Invalid IP address: " + broker.ipAddress);
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
        return broker;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toServerURI();
    }

    public String toConfigString() {
        StringBuilder sb = new StringBuilder();
        sb.append(uniqueId).append(FIELD_SEPARATOR);
        sb.append(port).append(FIELD_SEPARATOR);
        sb.append(hostName).append(FIELD_SEPARATOR);
        sb.append(ipAddress).append(FIELD_SEPARATOR);

        return sb.toString();
    }

    /**
     * Returns a URI representation of the broker
     * in the format [Protocol]://[ServerName]:[Port]
     *
     * @return The URI representation of the broker
     */
    String toServerURI() {
        StringBuilder sb = new StringBuilder();
        sb.append(SSL_PROTOCOL);
        sb.append("://");
        String serverName = (ServerNameHelper.isValidIPv6Address(hostName)
            ? "[" + hostName + "]"
            : hostName);
        sb.append(serverName);
        sb.append(":");
        sb.append(port);
        return sb.toString();
    }

    /**
     * Returns a alternative URI representation of the broker using its IP address
     * in the format [Protocol]://[ServerName]:[Port]
     *
     * @return The URI representation of the broker
     */
    String toAlternativeServerURI() {
        StringBuilder sb = new StringBuilder();
        sb.append(SSL_PROTOCOL);
        sb.append("://");
        String serverName;
        if (ipAddress != null && !ipAddress.isEmpty()) {
            serverName = (ServerNameHelper.isValidIPv6Address(ipAddress)
                ? "[" + ipAddress + "]"
                : ipAddress);
        } else {
            serverName = (ServerNameHelper.isValidIPv6Address(hostName)
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

    public String getUniqueId() {
        return uniqueId;
    }

    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    Long getResponseTime() {
        return responseTime;
    }

    void setResponseTime(Long responseTime) {
        this.responseTime = responseTime;
    }

    boolean isResponseFromIpAddress() {
        return responseFromIpAddress;
    }

    void setResponseFromIpAddress(boolean responseFromIpAddress) {
        this.responseFromIpAddress = responseFromIpAddress;
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
}

