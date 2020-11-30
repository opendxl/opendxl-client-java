package com.opendxl.client;

import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.SocketChannel;

/**
 * Helper SSL socket class for establishing connections via a proxy.
 */
class ProxiedSSLSocket extends SSLSocket {

    /**
     * The SSL socket factory
     */
    private final SSLSocketFactory socketFactory;

    /**
     * The socket containing proxy info
     */
    private final Socket proxySocket;

    /**
     * The actual socket created by the socketFactory with the proxySocket information
     */
    private SSLSocket socket;

    /**
     * Constructor
     *
     * @param socketFactory The SSL socket factory
     * @param proxySocket The socket containing proxy info
     */
    ProxiedSSLSocket(SSLSocketFactory socketFactory, Socket proxySocket) {
        this.socketFactory = socketFactory;
        this.proxySocket = proxySocket;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void connect(SocketAddress socketAddress) throws IOException {
        connect(socketAddress, 0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void connect(SocketAddress socketAddress, int timeout) throws IOException {
        try {
            proxySocket.connect(socketAddress, timeout);
        } catch (Throwable t) {
            throw new IOException(t);
        }
        this.socket = (SSLSocket) socketFactory.createSocket(proxySocket,
            ((InetSocketAddress) socketAddress).getHostName(), ((InetSocketAddress) socketAddress).getPort(), true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String[] getSupportedCipherSuites() {
        return this.socket.getSupportedCipherSuites();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String[] getEnabledCipherSuites() {
        return this.socket.getEnabledCipherSuites();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setEnabledCipherSuites(String[] strings) {
        this.socket.setEnabledCipherSuites(strings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String[] getSupportedProtocols() {
        return this.socket.getSupportedProtocols();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String[] getEnabledProtocols() {
        return this.socket.getEnabledProtocols();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setEnabledProtocols(String[] strings) {
        this.socket.setEnabledProtocols(strings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SSLSession getSession() {
        return this.socket.getSession();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SSLSession getHandshakeSession() {
        return this.socket.getHandshakeSession();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addHandshakeCompletedListener(HandshakeCompletedListener handshakeCompletedListener) {
        this.socket.addHandshakeCompletedListener(handshakeCompletedListener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeHandshakeCompletedListener(HandshakeCompletedListener handshakeCompletedListener) {
        this.socket.removeHandshakeCompletedListener(handshakeCompletedListener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void startHandshake() throws IOException {
        this.socket.startHandshake();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUseClientMode(boolean b) {
        this.socket.setUseClientMode(b);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean getUseClientMode() {
        return this.socket.getUseClientMode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setNeedClientAuth(boolean b) {
        this.socket.setNeedClientAuth(b);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean getNeedClientAuth() {
        return this.socket.getNeedClientAuth();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setWantClientAuth(boolean b) {
        this.socket.setWantClientAuth(b);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean getWantClientAuth() {
        return this.socket.getWantClientAuth();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setEnableSessionCreation(boolean b) {
        this.socket.setEnableSessionCreation(b);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean getEnableSessionCreation() {
        return this.socket.getEnableSessionCreation();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SSLParameters getSSLParameters() {
        return this.socket.getSSLParameters();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSSLParameters(SSLParameters sslParameters) {
        this.socket.setSSLParameters(sslParameters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void bind(SocketAddress socketAddress) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InetAddress getInetAddress() {
        return this.socket.getInetAddress();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InetAddress getLocalAddress() {
        return this.socket.getLocalAddress();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getPort() {
        return this.socket.getPort();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getLocalPort() {
        return this.socket.getLocalPort();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SocketAddress getRemoteSocketAddress() {
        return this.socket.getRemoteSocketAddress();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SocketAddress getLocalSocketAddress() {
        return this.socket.getLocalSocketAddress();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SocketChannel getChannel() {
        return this.socket.getChannel();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStream getInputStream() throws IOException {
        return this.socket.getInputStream();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OutputStream getOutputStream() throws IOException {
        return this.socket.getOutputStream();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTcpNoDelay(boolean b) throws SocketException {
        this.socket.setTcpNoDelay(b);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean getTcpNoDelay() throws SocketException {
        return this.socket.getTcpNoDelay();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSoLinger(boolean b, int i) throws SocketException {
        this.socket.setSoLinger(b, i);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getSoLinger() throws SocketException {
        return this.socket.getSoLinger();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendUrgentData(int i) throws IOException {
        this.socket.sendUrgentData(i);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setOOBInline(boolean b) throws SocketException {
        this.socket.setOOBInline(b);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean getOOBInline() throws SocketException {
        return this.socket.getOOBInline();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSoTimeout(int i) throws SocketException {
//        this.socket.setSoTimeout(i); // TODO ?
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getSoTimeout() throws SocketException {
        return this.socket.getSoTimeout();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSendBufferSize(int i) throws SocketException {
        this.socket.setSendBufferSize(i);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getSendBufferSize() throws SocketException {
        return this.socket.getSendBufferSize();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setReceiveBufferSize(int i) throws SocketException {
        this.socket.setReceiveBufferSize(i);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getReceiveBufferSize() throws SocketException {
        return this.socket.getReceiveBufferSize();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setKeepAlive(boolean b) throws SocketException {
        this.socket.setKeepAlive(b);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean getKeepAlive() throws SocketException {
        return this.socket.getKeepAlive();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTrafficClass(int i) throws SocketException {
        this.socket.setTrafficClass(i);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getTrafficClass() throws SocketException {
        return this.socket.getTrafficClass();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setReuseAddress(boolean b) throws SocketException {
        this.socket.setReuseAddress(b);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean getReuseAddress() throws SocketException {
        return this.socket.getReuseAddress();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException {
        this.socket.close();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void shutdownInput() throws IOException {
        this.socket.shutdownInput();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void shutdownOutput() throws IOException {
        this.socket.shutdownOutput();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return this.socket.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isConnected() {
        return this.socket.isConnected();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isBound() {
        return this.socket.isBound();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isClosed() {
        return this.socket.isClosed();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isInputShutdown() {
        return this.socket.isInputShutdown();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isOutputShutdown() {
        return this.socket.isOutputShutdown();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setPerformancePreferences(int i, int i1, int i2) {
        this.socket.setPerformancePreferences(i, i1, i2);
    }
}
