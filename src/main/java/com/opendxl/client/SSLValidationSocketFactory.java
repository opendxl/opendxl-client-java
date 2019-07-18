/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.security.KeyStore;
import java.security.SecureRandom;

/**
 * Helper class for SSL connections
 * <P>
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
     * @param keyStore The keystore
     * @param keyStorePassword The keystore
     * @return A new instance of an {@link javax.net.ssl.SSLSocketFactory} that validates presented certificates.
     * @throws Exception If an SSL exception occurs
     */
    public static SSLSocketFactory newInstance(final KeyStore keyStore, final String keyStorePassword)
        throws Exception {

        final TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);

        final KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, keyStorePassword.toCharArray());

        final SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), secureRandom);
        return sslContext.getSocketFactory();
    }
}
