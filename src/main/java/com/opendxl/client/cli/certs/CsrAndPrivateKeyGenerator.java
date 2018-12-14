package com.opendxl.client.cli.certs;

import org.apache.log4j.Logger;
import org.bouncycastle.openssl.PEMEncryptor;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.bouncycastle.util.io.pem.PemObject;

import javax.security.auth.x500.X500Principal;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.invoke.MethodHandles;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.List;

/**
 * Class used for generating a private key and certificate signing request (CSR).
 */
public class CsrAndPrivateKeyGenerator {

    /**
     * The logger
     */
    private static Logger logger = Logger.getLogger(MethodHandles.lookup().lookupClass());

    // TODO could this just be moved to another class and the CertUtils class?

    /**
     * SHA 256 with RSA indicator
     */
    private static final String SHA256_WITH_RSA = "SHA256WithRSA";

    /**
     * The key length (specified in number of bits)
     */
    private static final int KEY_BITS = 2048;
    /**
     * the public exponent
     */
    private static final int PUBLIC_EXPONENT = 0x10001;

    /**
     * The key-pair
     */
    private KeyPair keyPair;
    /**
     * The X509 Certificate
     */
    private PKCS10CertificationRequest csr;

    /**
     * Constructor that generates a key pair and CSR
     *
     * @param x509DistinguishedNames  The string containing X509 distinguished names
     * @param subjectAlternativeNames A list of certificate subject alternative names
     * @throws InvalidAlgorithmParameterException If there is an issue generating the key pair
     * @throws NoSuchAlgorithmException           If there is an issue generating the key pair
     * @throws OperatorCreationException          If there is an issue generating the key pair or CSR
     */
    public CsrAndPrivateKeyGenerator(String x509DistinguishedNames, List<String> subjectAlternativeNames)
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, OperatorCreationException {
        this.keyPair = generateKeyPair();
        this.csr = generateCSR(x509DistinguishedNames, this.keyPair, subjectAlternativeNames);
    }

    /**
     * Method to generate a key pair
     *
     * @return A new key pair
     * @throws InvalidAlgorithmParameterException If there is an issue generating the key pair
     * @throws NoSuchAlgorithmException           If there is an issue generating the key pair
     */
    private KeyPair generateKeyPair() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException {
        final AlgorithmParameterSpec spec =
                new RSAKeyGenParameterSpec(KEY_BITS, BigInteger.valueOf(PUBLIC_EXPONENT));
        final KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(spec);
        return kpg.generateKeyPair();
    }

    /**
     * Method  to generate a CSR
     *
     * @param x509distinguishedNames  The string containing X509 distinguished names
     * @param keyPair                 The key pair containing the private and public keys used to generate the CSR
     * @param subjectAlternativeNames A list of certificate subject alternative names
     * @return A CSR
     * @throws OperatorCreationException If there is an issue generating the CSR
     */
    private PKCS10CertificationRequest generateCSR(String x509distinguishedNames, KeyPair keyPair,
                                                   List<String> subjectAlternativeNames)
            throws OperatorCreationException {

        // TODO add code to set SAN's and other items he sets in python
        // The certificate subject format string
        final X500Principal subject = new X500Principal(x509distinguishedNames);

        // Switch to X509v3CertificateBuilder ???
        final PKCS10CertificationRequestBuilder crBuilder =
                new JcaPKCS10CertificationRequestBuilder(subject, keyPair.getPublic());
        final JcaContentSignerBuilder csBuilder = new JcaContentSignerBuilder(SHA256_WITH_RSA);
        final ContentSigner signer = csBuilder.build(keyPair.getPrivate());
        return crBuilder.build(signer);
    }

    /**
     * Method to save the CSR and private key to disk
     *
     * @param csrFileName        The CSR file name
     * @param privateKeyFileName The private key file name
     * @param passphrase         Encryption to use when saving the private key
     * @throws IOException If there is an error saving the CSR or private key to disk
     */
    public void saveCsrAndPrivateKey(String csrFileName, String privateKeyFileName, String passphrase)
            throws IOException {
        PEMEncryptor pemEncryptor = null; // TODO deal with encryption
        logger.info("Saving csr file to " + csrFileName);
        CertUtils.writePemFile(privateKeyFileName, new PemObject(CertUtils.PRIVATE_KEY_OBJECT_TYPE_STRING,
                this.keyPair.getPrivate().getEncoded()), pemEncryptor);
        logger.info("Saving private key file to " + privateKeyFileName);
        CertUtils.writePemFile(csrFileName, new PemObject(CertUtils.CERTIFICATE_REQUEST_OBJECT_TYPE_STRING,
                this.csr.getEncoded()), null);
    }

    /**
     * Method to get the CSR as a PEM encoded string
     *
     * @return The CSR as a PEM encoded string
     * @throws IOException If there is an issue converting the CSR from PKCS10CertificationRequest to a PEM
     *                     encoded string.
     */
    public String getCsrAsPemString() throws IOException {
        try (StringWriter stringWriter = new StringWriter();
             JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter)) {
            pemWriter.writeObject(new PemObject(CertUtils.CERTIFICATE_REQUEST_OBJECT_TYPE_STRING,
                    this.csr.getEncoded()));
            pemWriter.flush();
            return stringWriter.toString();
        }
    }
}
