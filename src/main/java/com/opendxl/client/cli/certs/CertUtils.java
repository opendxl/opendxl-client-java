package com.opendxl.client.cli.certs;

import org.bouncycastle.openssl.PEMEncryptor;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.util.io.pem.PemObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class CertUtils {
    /**
     * Denotes a certificate request
     */
    public static final String CERTIFICATE_REQUEST_OBJECT_TYPE_STRING = "CERTIFICATE REQUEST";

    /**
     * Denotes a private key
     */
    public static final String PRIVATE_KEY_OBJECT_TYPE_STRING = "PRIVATE KEY";

    /**
     * Denotes a private key
     */
    public static final String CERTIFICATE_OBJECT_TYPE_STRING = "CERTIFICATE";


    private CertUtils() {
    }

    public static void writePemFile(String fileName, PemObject pemObject, PEMEncryptor pemEncryptor)
            throws IOException {
        File file = new File(fileName.substring(0, fileName.lastIndexOf(File.separator)));
        if (!file.exists()) {
            file.mkdirs();
        }

        try (JcaPEMWriter pemWriter = new JcaPEMWriter(new FileWriter(fileName))) {
            pemWriter.writeObject(pemObject, pemEncryptor);
        }
    }
}
