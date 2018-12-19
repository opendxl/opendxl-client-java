/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client.cli;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.ExtensionsGenerator;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.bouncycastle.util.io.pem.PemObject;

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
import java.util.ArrayList;
import java.util.List;

/**
 * Class used for generating a private key and certificate signing request (CSR).
 */
class CsrAndPrivateKeyGenerator {

    /**
     * The logger
     */
    private static Logger logger = Logger.getLogger(MethodHandles.lookup().lookupClass());

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
     * The CLI input crypto arguments
     */
    private CryptoArgs cryptoArgs;

    /**
     * Constructor that generates a key pair and CSR
     *
     * @param commonName The name to be used as the Common Name (CN) in the Subject DN of the CSR
     * @param cryptoArgs The CLI input crypto related arguments
     * @throws InvalidAlgorithmParameterException If there is an issue generating the key pair
     * @throws NoSuchAlgorithmException           If there is an issue generating the key pair
     * @throws OperatorCreationException          If there is an issue generating the key pair or CSR
     */
    CsrAndPrivateKeyGenerator(String commonName, CryptoArgs cryptoArgs)
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, OperatorCreationException,
            IOException {
        this.cryptoArgs = cryptoArgs;
        this.keyPair = generateKeyPair();
        this.csr = generateCSR(commonName, this.keyPair);
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
     * @param commonName The name to be used as the Common Name (CN) in the Subject DN of the CSR
     * @param keyPair    The key pair containing the private and public keys used to generate the CSR
     * @return A CSR
     * @throws OperatorCreationException If there is an issue generating the CSR
     * @throws IOException               If there is an issue generating the extensions object
     */
    private PKCS10CertificationRequest generateCSR(String commonName, KeyPair keyPair)
            throws OperatorCreationException, IOException {
        // Switch to X509v3CertificateBuilder ???
        final PKCS10CertificationRequestBuilder crBuilder = new JcaPKCS10CertificationRequestBuilder(
                generateX509DistinguishedNames(commonName), keyPair.getPublic());
        // Add the extensions to the CSR builder
        crBuilder.addAttribute(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, generateCsrExtensions());
        final JcaContentSignerBuilder csBuilder = new JcaContentSignerBuilder(SHA256_WITH_RSA);
        final ContentSigner signer = csBuilder.build(keyPair.getPrivate());
        return crBuilder.build(signer);
    }

    /**
     * Method to generate the CSR extensions object
     *
     * @return CSR extensions object
     * @throws IOException If there is an issue generating the extensions object
     */
    private Extensions generateCsrExtensions() throws IOException {
        // Create CSR extensions
        ExtensionsGenerator extensionsGenerator = new ExtensionsGenerator();
        // basic constraints extension
        extensionsGenerator.addExtension(Extension.basicConstraints, false,
                new BasicConstraints(false));
        // key usage extension
        extensionsGenerator.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
        // Extended key usage extension
        extensionsGenerator.addExtension(Extension.extendedKeyUsage, false,
                new ExtendedKeyUsage(KeyPurposeId.id_kp_clientAuth));

        //Subject Alternative Name extensions
        if (this.cryptoArgs.getSubjectAlternativeNames() != null
                && !this.cryptoArgs.getSubjectAlternativeNames().isEmpty()) {
            List<GeneralName> sanGeneralNames = new ArrayList<>();
            for (String san : this.cryptoArgs.getSubjectAlternativeNames()) {
                sanGeneralNames.add(new GeneralName(GeneralName.dNSName, san));
            }
            extensionsGenerator.addExtension(Extension.subjectAlternativeName, false,
                    new GeneralNames(sanGeneralNames.toArray(new GeneralName[sanGeneralNames.size()])));
        }

        return extensionsGenerator.generate();
    }

    /**
     * Create the string containing the X509 distinguished names
     *
     * @param commonName The name to be used as the Common Name (CN) in the Subject DN of the CSR
     * @return The string containing the X509 distinguished names
     */
    private X500Name generateX509DistinguishedNames(String commonName) {
        X500NameBuilder nameBuilder = new X500NameBuilder(X500Name.getDefaultStyle());
        nameBuilder.addRDN(BCStyle.CN, commonName);
        if (StringUtils.isNotBlank(this.cryptoArgs.getOrganizationalUnit())) {
            nameBuilder.addRDN(BCStyle.OU, this.cryptoArgs.getOrganizationalUnit());
        }
        if (StringUtils.isNotBlank(this.cryptoArgs.getOrganization())) {
            nameBuilder.addRDN(BCStyle.O, this.cryptoArgs.getOrganization());
        }
        if (StringUtils.isNotBlank(this.cryptoArgs.getLocality())) {
            nameBuilder.addRDN(BCStyle.L, this.cryptoArgs.getLocality());
        }
        if (StringUtils.isNotBlank(this.cryptoArgs.getStateOrProvince())) {
            nameBuilder.addRDN(BCStyle.ST, this.cryptoArgs.getStateOrProvince());
        }
        if (StringUtils.isNotBlank(this.cryptoArgs.getCountry())) {
            nameBuilder.addRDN(BCStyle.C, this.cryptoArgs.getCountry());
        }
        if (StringUtils.isNotBlank(this.cryptoArgs.getEmail())) {
            nameBuilder.addRDN(BCStyle.EmailAddress, this.cryptoArgs.getEmail());
        }

        return nameBuilder.build();
    }

    /**
     * Method to save the CSR and private key to disk
     *
     * @param configDir The configuration directory
     * @throws IOException If there is an error saving the CSR or private key to disk
     */
    void saveCsrAndPrivateKey(String configDir)
            throws IOException {
        String csrFileName = this.cryptoArgs.getCsrFileName(configDir);
        String privateKeyFileName = this.cryptoArgs.getPrivateKeyFileName(configDir);

        // Save CSR
        logger.info("Saving csr file to " + csrFileName);
        CertUtils.writePemFile(csrFileName, new PemObject(CertUtils.CERTIFICATE_REQUEST_OBJECT_TYPE_STRING,
                this.csr.getEncoded()));

        // Save private key
        logger.info("Saving private key file to " + privateKeyFileName);
        CertUtils.writePemFile(privateKeyFileName, new PemObject(CertUtils.PRIVATE_KEY_OBJECT_TYPE_STRING,
                this.keyPair.getPrivate().getEncoded()));
    }

    /**
     * Method to get the CSR as a PEM encoded string
     *
     * @return The CSR as a PEM encoded string
     * @throws IOException If there is an issue converting the CSR from PKCS10CertificationRequest to a PEM
     *                     encoded string.
     */
    String getCsrAsPemString() throws IOException {
        try (StringWriter stringWriter = new StringWriter();
             JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter)) {
            pemWriter.writeObject(new PemObject(CertUtils.CERTIFICATE_REQUEST_OBJECT_TYPE_STRING,
                    this.csr.getEncoded()));
            pemWriter.flush();
            return stringWriter.toString();
        }
    }
}
