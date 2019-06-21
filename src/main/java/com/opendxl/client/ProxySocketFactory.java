package com.opendxl.client;

import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * Helper factory class for establishing connections via a proxy.
 */
class ProxySocketFactory extends SSLSocketFactory {

    /**
     * The SSL socket factory delegate
     */
    private final SSLSocketFactory delegate;

    /**
     * The proxy information
     */
    private final Proxy proxy;

    /**
     * Default constructor
     */
    ProxySocketFactory() {
        this((SSLSocketFactory) SSLSocketFactory.getDefault(), resolveProxyHost(), resolveProxyPort());
    }

    /**
     * Constructor
     *
     * @param delegate The SSL socket factory delegate
     * @param proxyHost The proxy host
     * @param proxyPort The proxy port
     */
    ProxySocketFactory(SSLSocketFactory delegate, String proxyHost, int proxyPort) {
        this.delegate = delegate;
        this.proxy = buildProxy(proxyHost, proxyPort);
    }

    /**
     * Method to get the proxy port form a system property
     *
     * @return The proxy port
     */
    private static Integer resolveProxyPort() {
        return Integer.valueOf(System.getProperty("http.proxyPort", "3128"));
    }

    /**
     * Method to get the proxy host from a system property
     *
     * @return The proxy host
     */
    private static String resolveProxyHost() {
        return System.getProperty("http.proxyHost", "localhost");
    }

    /**
     * Method to build a proxy object
     *
     * @param proxyHost The proxy host
     * @param proxyPort The proxy port
     * @return A proxy object based on the input proxy host and proxy port
     */
    private Proxy buildProxy(String proxyHost, int proxyPort) {
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Socket createSocket() throws IOException {
        return new ProxiedSSLSocket(delegate, new Socket(proxy));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String[] getDefaultCipherSuites() {
        return delegate.getDefaultCipherSuites();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String[] getSupportedCipherSuites() {
        return delegate.getSupportedCipherSuites();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        return delegate.createSocket(s, host, port, autoClose);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
        return delegate.createSocket(host, port);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
        throws IOException, UnknownHostException {
        return delegate.createSocket(host, port, localHost, localPort);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return delegate.createSocket(host, port);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
        throws IOException {
        return delegate.createSocket(address, port, localAddress, localPort);
    }

}