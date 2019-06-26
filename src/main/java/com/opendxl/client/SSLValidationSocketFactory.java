/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client;

import org.apache.commons.lang3.StringUtils;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.security.KeyStore;
import java.security.SecureRandom;

/**
 * Helper class for SSL connections
 * <p>
 * Performs validation of presented server certificates and client authentication
 * </P>
 */
class SSLValidationSocketFactory {

    /**
     * Secure random
     */
    private static SecureRandom secureRandom = new SecureRandom();

    /**
     * Private constructor
     */
    private SSLValidationSocketFactory() {
        super();
    }

    /**
     * Returns a new instance of an {@link javax.net.ssl.SSLSocketFactory} that validates presented certificates.
     * We always return a new instance to avoid caching which wouldn't accurately represent a separate client
     * connecting to a broker.
     *
     * @param keyStore         The keystore
     * @param keyStorePassword The keystore
     * @return A new instance of an {@link javax.net.ssl.SSLSocketFactory} that validates presented certificates.
     * @throws Exception If an SSL exception occurs
     */
    public static SSLSocketFactory newInstance(final KeyStore keyStore, final String keyStorePassword,
                                               final boolean useWebSockets, final String proxyHost,
                                               final int proxyPort, final String proxyUserName,
                                               final char[] proxyPassword)
        throws Exception {

        final TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);

        final KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, keyStorePassword.toCharArray());

        final SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), secureRandom);

        // If there is no proxy return a non proxy socket factory
        if (!useWebSockets || StringUtils.isBlank(proxyHost)) {
            return sslContext.getSocketFactory();
        }

        // Create proxy socket factory
        ProxySocketFactory proxySocketFactory =
            new ProxySocketFactory(sslContext.getSocketFactory(), proxyHost, proxyPort);

        // Set the default Authenticator if there is a proxy username and password
        if (StringUtils.isNotBlank(proxyUserName)) {
            // Basic authentication was disabled in Java 8
            // (https://www.oracle.com/technetwork/java/javase/8u111-relnotes-3124969.html)
            // This is a workaround to re-enable basic authentication when making a proxy connection.
            // The other alternative is to set this as a system property when starting the OpenDXL Java Client:
            // -Djdk.http.auth.tunneling.disabledSchemes= ""
            System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
            Authenticator.setDefault(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(proxyUserName, proxyPassword);
                }
            });
        }

        return proxySocketFactory;
    }
}
