/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client.cli;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.util.io.pem.PemObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Collection;

/**
 * Utility class for dealing with certificates.
 */
class CertUtils {
    /**
     * The logger
     */
    private static Logger logger = Logger.getLogger(MethodHandles.lookup().lookupClass());

    /**
     * Denotes a certificate request
     */
    public static final String CERTIFICATE_REQUEST_OBJECT_TYPE_STRING = "CERTIFICATE REQUEST";

    /**
     * Denotes a private key
     */
    public static final String PRIVATE_KEY_OBJECT_TYPE_STRING = "PRIVATE KEY";

    /**
     * Denotes a certiface
     */
    public static final String CERTIFICATE_OBJECT_TYPE_STRING = "CERTIFICATE";

    /**
     * Begin cert string
     */
    public static final String BEGIN_CERT = "-----BEGIN CERTIFICATE-----";

    /**
     * Private constructor.
     */
    private CertUtils() {
    }

    /**
     * Method for writing a PEM file to disk.
     *
     * @param fileName  The name of the file to write to disk. Can include an absolute path.
     * @param pemObject The PEM object to write to disk.
     * @throws IOException If there is an issue writing the file to disk
     */
    static void writePemFile(String fileName, PemObject pemObject)
            throws IOException {
        File file = new File(fileName.substring(0, fileName.lastIndexOf(File.separator)));
        if (!file.exists()) {
            file.mkdirs();
        }

        try (JcaPEMWriter pemWriter = new JcaPEMWriter(new FileWriter(fileName))) {
            pemWriter.writeObject(pemObject);
        }
    }

    /**
     * Method for writing a certificate chain to single file on disk
     *
     * @param fileName  The name of the file to write to disk. Can include an absolute path.
     * @param certChain The certificate chain of certificates to write to a single file.
     * @throws IOException If there is an issue writing the file to disk
     */
    static void writePemFile(String fileName, Collection<? extends Certificate> certChain) throws IOException {
        File file = new File(fileName);
        logger.info(String.format("Saving %s file to %s", file.getName(), file.getParent()));

        StringBuilder sb = new StringBuilder();
        for (Certificate certificate : certChain) {
            try (StringWriter sw = new StringWriter(); JcaPEMWriter pemWriter = new JcaPEMWriter(sw)) {
                pemWriter.writeObject(certificate);
                pemWriter.flush();
                sb.append(sw.toString());
            }
        }

        try (FileWriter fileWriter = new FileWriter(fileName)) {
            fileWriter.write(sb.toString());
        }
    }

    /**
     * Method for writing a certificate string to disk. The method writes all certificates in the input string
     * to PEM format in a single file on disk.
     *
     * @param fileName   The name of the file to write to disk. Can include an absolute path.
     * @param certString The string containing one or more certificates
     * @throws Exception If there is an issue converting the input certificate string to one or more certificates
     */
    static void writePemFile(String fileName, String certString) throws Exception {
        if (StringUtils.isBlank(certString)) {
            throw new Exception("Cert string is blank");
        }

        int certChainBeginCertCount = StringUtils.countMatches(certString, CertUtils.BEGIN_CERT);

        final CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        final Collection<? extends Certificate> certChain =
                certFactory.generateCertificates(
                        new ByteArrayInputStream(certString.getBytes(StandardCharsets.UTF_8)));

        if (certChain.isEmpty() || certChain.size() != certChainBeginCertCount) {
            throw new Exception("Invalid certificate returned from the cert string: "
                    + certString);
        }

        CertUtils.writePemFile(fileName, certChain);
    }
}
