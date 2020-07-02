package com.opendxl.client;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

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
            final List<Certificate> keyEntryCertChain = new ArrayList<>();
            //add cert
            keyEntryCertChain.add(cert);

            //build cert chain starting with the issuer ending with the root
            final List<Certificate> caCertsList = new ArrayList(Arrays.asList(caChain));
            Certificate signer = findIssuer(caCertsList, cert);
            while (signer != null) {
                //add the CA
                keyEntryCertChain.add(signer);
                signer = findIssuer(caCertsList, signer);
            }

            clientKeyStore.setKeyEntry(CLIENT_KEY_ALIAS, privateKey, keyStorePassword.toCharArray(),
                    keyEntryCertChain.toArray(new Certificate[0]));
        } finally {
            Arrays.fill(keyStorePassword.toCharArray(), '0');  //clear the password
        }

        return clientKeyStore;
    }

    /**
     * Helper method to get the Issuer Certificate.
     *
     * @param caChain CA chain
     * @param cert Certificate to find the issuer
     * @return Issuer CA Certificate or null if not found or we are at the root
     */
    private static Certificate findIssuer(final List<Certificate> caChain, final Certificate cert) {
        final X509Certificate x509Cert = ((X509Certificate) cert);
        if (x509Cert != null) {
            final Principal issuerDN = x509Cert.getIssuerDN();
            for (Certificate ca : caChain) {
                final X509Certificate x509Ca = ((X509Certificate) ca);
                if (x509Ca != null) {
                    //root ca found ..return null
                    if (cert.equals(x509Ca)) {
                       return null;
                    }
                    final Principal subjectDN = x509Ca.getSubjectDN();
                    if (issuerDN.equals(subjectDN)) {
                        return ca;
                    }
                }
            }
        }
        return null;
    }
}
