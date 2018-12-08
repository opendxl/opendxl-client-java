package com.opendxl.client.cli.certs;

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
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.List;

public class CsrAndPrivateKeyGenerator {
    /**
     * Denotes a certificate request
     */
    private static final String CERTIFICATE_REQUEST = "CERTIFICATE REQUEST";

    /**
     * Denotes a private key
     */
    private static final String PRIVATE_KEY = "PRIVATE KEY";

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


    public CsrAndPrivateKeyGenerator(String x509DistinguishedNames, List<String> subjectAlternativeNames)
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, OperatorCreationException {
        this.keyPair = generateKeyPair();
        this.csr = generateCSR(x509DistinguishedNames, this.keyPair, subjectAlternativeNames);
    }

    private KeyPair generateKeyPair() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException {
        final AlgorithmParameterSpec spec =
                new RSAKeyGenParameterSpec(KEY_BITS, BigInteger.valueOf(PUBLIC_EXPONENT));
        final KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(spec);
        return kpg.generateKeyPair();
    }

    private PKCS10CertificationRequest generateCSR(String x509distinguishedNames, KeyPair keyPair,
                                                   List<String> subjectAlertnativeNames)
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

    public void saveCsrAndPrivateKey(String csrFileName, String privateKeyFileName, String passphrase)
            throws IOException {
        PEMEncryptor pemEncryptor = null; // TODO deal with encryption
        writePemFile(privateKeyFileName, new PemObject("PRIVATE KEY", this.keyPair.getPrivate().getEncoded()),
                pemEncryptor);
        writePemFile(csrFileName, new PemObject(CERTIFICATE_REQUEST, this.csr.getEncoded()), null);
    }

    // TODO put in util method?
    private void writePemFile(String fileName, PemObject pemObject, PEMEncryptor pemEncryptor) throws IOException {
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(new FileWriter(fileName))) {
            pemWriter.writeObject(pemObject, pemEncryptor);
        }
    }

    public String getCsrAsPemString() throws IOException {
        try (StringWriter stringWriter = new StringWriter();
             JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter)) {
            pemWriter.writeObject(new PemObject(CERTIFICATE_REQUEST, this.csr.getEncoded()));
            pemWriter.flush();
            return stringWriter.toString();
        }
    }
}
