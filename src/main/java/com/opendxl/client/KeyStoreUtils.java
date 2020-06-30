package com.opendxl.client;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Collection;

/**
 * Utility methods for generating Java {@link KeyStore} instances from store-related files
 * (CA, cert, private key, etc.).
 */
class KeyStoreUtils {

    /**
     * The name of the DXL client key alias
     */
    private static final String CLIENT_KEY_ALIAS = "dxlClient";

    /**
     * The name of the broker CA alias
     */
    private static final String BROKER_CA_ALIAS = "brokerCA";


    /** Private constructor */
    private KeyStoreUtils() {
        super();
    }

    /**
     * Generates and returns a Java {@link KeyStore} corresponding to the specified file paths
     *
     * @param brokerCaFilePath Path to the broker CA file
     * @param certFilePath  Path to the certificate file
     * @param keyFilePath Path to the private key file
     * @param keyStorePassword The key store password
     * @return A Java {@link KeyStore} corresponding to the specified file paths
     * @throws Exception If an error occurs
     */
    static KeyStore generateKeyStoreFromFiles(
        final String brokerCaFilePath,
        final String certFilePath,
        final String keyFilePath,
        final String keyStorePassword)
        throws Exception {

        final CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        // Get the CA Chain
        final Collection<? extends Certificate> chain =
            certFactory.generateCertificates(
                new ByteArrayInputStream(FileUtils.readFileToByteArray(new File(brokerCaFilePath))));

        // Get the cert
        final Certificate cert =
            certFactory.generateCertificate(
                new ByteArrayInputStream(FileUtils.readFileToByteArray(new File(certFilePath))));

        // Load the private key and convert it to DER format from PEM
        String privateKeyPem = FileUtils.readFileToString(new File(keyFilePath));
        privateKeyPem = privateKeyPem.replace("-----BEGIN PRIVATE KEY-----", "");
        privateKeyPem = privateKeyPem.replace("-----END PRIVATE KEY-----", "");

        final KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        final PrivateKey privateKey = keyFactory.generatePrivate(
            new PKCS8EncodedKeySpec(new Base64().decode(privateKeyPem)));

        return generateKeystore(
            chain.toArray(new Certificate[0]), cert, keyStorePassword, privateKey);
    }

    /**
     * Generates and returns a Java {@link KeyStore} corresponding to the specified values
     *
     * @param caChain The CA chain
     * @param cert The certificate
     * @param keyStorePassword The key store password
     * @param privateKey The private key
     * @return A Java {@link KeyStore} corresponding to the specified values
     * @throws Exception If an error occurs
     */
    private static KeyStore generateKeystore(
        final Certificate[] caChain, final Certificate cert,
        final String keyStorePassword, final PrivateKey privateKey)
        throws Exception {
        if (caChain == null || cert == null || privateKey == null) {
            throw new IllegalArgumentException("Null values are not allowed when attempting to generate keystore");
        }

        final KeyStore clientKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        clientKeyStore.load(null, null);

        for (int i = 0; i < caChain.length; i++) {
            Certificate caCert = caChain[i];
            clientKeyStore.setCertificateEntry(BROKER_CA_ALIAS + i, caCert);
        }

        try {
            //build the certificate and chain for the key entry
            Certificate[] certChain = new Certificate[caChain.length + 1];
            certChain[0] = cert;
            for (int i = 0; i < caChain.length; i++) {
                certChain[i + 1] = caChain[(caChain.length - i) - 1];
            }
            clientKeyStore.setKeyEntry(CLIENT_KEY_ALIAS, privateKey, keyStorePassword.toCharArray(), certChain);
        } finally {
            Arrays.fill(keyStorePassword.toCharArray(), '0');  //clear the password
        }

        return clientKeyStore;
    }
}
