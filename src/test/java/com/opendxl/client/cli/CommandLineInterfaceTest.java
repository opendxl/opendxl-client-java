package com.opendxl.client.cli;

import org.apache.commons.io.FileUtils;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1String;
import org.bouncycastle.asn1.pkcs.Attribute;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


/**
 * Tests for the CommandLineInterface class
 */
public class CommandLineInterfaceTest {

    /**
     * Main usage help string
     */
    private static final String MAIN_USAGE_HELP = String.format(Locale.ROOT,
            "Usage: java -jar dxlclient-all.jar [-hV] [--verbose] [COMMAND]%n" +
                    "%n" +
                    "optional arguments:%n" +
                    "      --verbose   Verbose mode. Increases the log level to DEBUG%n" +
                    "  -h, --help      Show this help message and exit.%n" +
                    "  -V, --version   Print version information and exit.%n" +
                    "Commands:%n" +
                    "  generatecsr      Generate CSR and private key%n" +
                    "  provisionconfig  Download and provision the DXL client configuration%n" +
                    "  updateconfig     Update the DXL client configuration%n");

    /**
     * Usage help string fo the provision dxl client command
     */
    private static final String PROVISION_USAGE = String.format(Locale.ROOT,
            "Usage: java -jar dxlclient-all.jar provisionconfig [-hV] [--verbose]%n" +
                    "                                                   [--country=COUNTRY]%n" +
                    "                                                   [--email-address=EMAIL]%n" +
                    "                                                   [--locality=LOCALITY]%n" +
                    "                                                   [--organization=ORG]%n" +
                    "                                                   [--organizational-unit=ORG_UN%n" +
                    "                                                   IT]%n" +
                    "                                                   [--state-or-province=STATE]%n" +
                    "                                                   [-e=TRUSTSTORE_FILE]%n" +
                    "                                                   [-f=PREFIX] [-p=PASSWORD]%n" +
                    "                                                   [-P=PASS]%n" +
                    "                                                   [-r=<certRequestFile>]%n" +
                    "                                                   [-t=PORT] [-u=USERNAME] [-s%n" +
                    "                                                   [=NAME...]]... CONFIGDIR%n" +
                    "                                                   HOSTNAME%n" +
                    "                                                   COMMON_OR_CSRFILE_NAME%n" +
                    "Download and provision the DXL client configuration%n" +
                    "%n" +
                    "positional arguments:%n" +
                    "      CONFIGDIR              Path to the config directory%n" +
                    "      HOSTNAME               Hostname where the management service resides%n" +
                    "      COMMON_OR_CSRFILE_NAME If \"-r\" is specified, interpret as the filename for a%n" +
                    "                               pre-existing csr. If \"-r\" is not specified, use as%n" +
                    "                               the Common Name (CN) in the Subject DN for a new csr.%n" +
                    "%n" +
                    "optional arguments:%n" +
                    "      --country=COUNTRY      Country (C) to use in the CSR's Subject DN%n" +
                    "      --email-address=EMAIL  e-mail address to use in the CSR's Subject DN%n" +
                    "      --locality=LOCALITY    Locality (L) to use in the CSR's Subject DN%n" +
                    "      --organization=ORG     Organization (O) to use in the CSR's Subject DN%n" +
                    "      --organizational-unit=ORG_UNIT%n" +
                    "                             Organizational Unit (OU) to use in the CSR's Subject DN%n" +
                    "      --state-or-province=STATE%n" +
                    "                             State or province (ST) to use in the CSR's Subject DN%n" +
                    "      --verbose              Verbose mode. Increases the log level to DEBUG%n" +
                    "  -e, --truststore=TRUSTSTORE_FILE%n" +
                    "                             Name of file containing one or more CA pems to use in%n" +
                    "                               validating the management server%n" +
                    "  -f, --file-prefix=PREFIX   file prefix to use for CSR, key, and cert files.%n" +
                    "                               (default: client)%n" +
                    "  -h, --help                 Show this help message and exit.%n" +
                    "  -p, --password=PASSWORD    Password for the management service user%n" +
                    "  -P, --passphrase=PASS      private key passphrase%n" +
                    "  -r, --cert-request-file=<certRequestFile>%n" +
                    "                             Interpret COMMON_OR_CSRFILE_NAME as a filename for an%n" +
                    "                               existing csr to be signed. If not specified, a new%n" +
                    "                               csr is generated.%n" +
                    "  -s, --san[=NAME...]        add Subject Alternative Name(s) to the CSR%n" +
                    "  -t, --port=PORT            Port where the management service resides%n" +
                    "  -u, --user=USERNAME        User registered at the management service%n" +
                    "  -V, --version              Print version information and exit.%n");

    /**
     * Usage help string for the generatecsr CLI command
     */
    private static final String GENERATE_CSR_USAGE =
            String.format(Locale.ROOT, "Usage: java -jar dxlclient-all.jar generatecsr [-hV] [--verbose]%n" +
                    "                                               [--country=COUNTRY]%n" +
                    "                                               [--email-address=EMAIL]%n" +
                    "                                               [--locality=LOCALITY]%n" +
                    "                                               [--organization=ORG]%n" +
                    "                                               [--organizational-unit=ORG_UNIT]%n" +
                    "                                               [--state-or-province=STATE]%n" +
                    "                                               [-f=PREFIX] [-P=PASS] [-s%n" +
                    "                                               [=NAME...]]... CONFIGDIR%n" +
                    "                                               COMMON_NAME%n" +
                    "Generate CSR and private key%n" +
                    "%n" +
                    "positional arguments:%n" +
                    "      CONFIGDIR              Path to the config directory%n" +
                    "      COMMON_NAME            Common Name (CN) to use in the CSR's Subject DN%n" +
                    "%n" +
                    "optional arguments:%n" +
                    "      --country=COUNTRY      Country (C) to use in the CSR's Subject DN%n" +
                    "      --email-address=EMAIL  e-mail address to use in the CSR's Subject DN%n" +
                    "      --locality=LOCALITY    Locality (L) to use in the CSR's Subject DN%n" +
                    "      --organization=ORG     Organization (O) to use in the CSR's Subject DN%n" +
                    "      --organizational-unit=ORG_UNIT%n" +
                    "                             Organizational Unit (OU) to use in the CSR's Subject DN%n" +
                    "      --state-or-province=STATE%n" +
                    "                             State or province (ST) to use in the CSR's Subject DN%n" +
                    "      --verbose              Verbose mode. Increases the log level to DEBUG%n" +
                    "  -f, --file-prefix=PREFIX   file prefix to use for CSR, key, and cert files.%n" +
                    "                               (default: client)%n" +
                    "  -h, --help                 Show this help message and exit.%n" +
                    "  -P, --passphrase=PASS      private key passphrase%n" +
                    "  -s, --san[=NAME...]        add Subject Alternative Name(s) to the CSR%n" +
                    "  -V, --version              Print version information and exit.%n");

    /**
     * Usage help string for the updateconfig CLI command
     */
    private static final String UPDATE_CONFIG_USAGE = String.format(Locale.ROOT,
            "Usage: java -jar dxlclient-all.jar updateconfig [-hV] [--verbose]%n" +
                    "                                                [-e=TRUSTSTORE_FILE]%n" +
                    "                                                [-p=PASSWORD] [-t=PORT]%n" +
                    "                                                [-u=USERNAME] CONFIGDIR HOSTNAME%n" +
                    "Update the DXL client configuration%n" +
                    "%n" +
                    "positional arguments:%n" +
                    "      CONFIGDIR             Path to the config directory%n" +
                    "      HOSTNAME              Hostname where the management service resides%n" +
                    "%n" +
                    "optional arguments:%n" +
                    "      --verbose             Verbose mode. Increases the log level to DEBUG%n" +
                    "  -e, --truststore=TRUSTSTORE_FILE%n" +
                    "                            Name of file containing one or more CA pems to use in%n" +
                    "                              validating the management server%n" +
                    "  -h, --help                Show this help message and exit.%n" +
                    "  -p, --password=PASSWORD   Password for the management service user%n" +
                    "  -t, --port=PORT           Port where the management service resides%n" +
                    "  -u, --user=USERNAME       User registered at the management service%n" +
                    "  -V, --version             Print version information and exit.%n");

    /**
     * Provision DXL Client CLI command
     */
    private static final String PROVISION_COMMAND = "provisionconfig";
    /**
     * Generate a CSR CLI command
     */
    private static final String GENERATE_CSR_COMMAND = "generatecsr";
    /**
     * Update the DXL Config CLI command
     */
    private static final String UPDATE_CONFIG_COMMAND = "updateconfig";

    private static final String TEMP_FILE_DIR_NAME = "tempFileDir";

    private static final String CSR_COMMON_NAME = "client1";

    private static final String DEFAULT_FILE_PREFIX = "client";

    /**
     * The System Out rule
     */
    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().mute();
    /**
     * The System Error rule
     */
    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().mute();
    /**
     * The Expected System Exit
     */
    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();

    @Test
    public void testShowVersionInfo() {
        exit.checkAssertionAfterwards(() -> assertEquals("Unexpected output from CLI",
                "null version: null" + System.lineSeparator(),
                systemOutRule.getLog()));
        CommandLineInterface.main(new String[] {"-V"});
    }

    @Test
    public void testShowMainUsageHelp() {
        exit.checkAssertionAfterwards(() -> assertEquals("Unexpected output from CLI for usage help",
                MAIN_USAGE_HELP,
                systemOutRule.getLog()));
        // With -h arg
        CommandLineInterface.main(new String[] {"-h"});
        // Clear System.out
        systemOutRule.clearLog();
        // With no args
        CommandLineInterface.main(new String[] {});
    }

    @Test
    public void testShowProvisionUsageHelp() {
        exit.checkAssertionAfterwards(() -> assertEquals(
                "Unexpected output from CLI for provisionconfig usage help",
                PROVISION_USAGE,
                systemOutRule.getLog()));
        // With -h arg
        CommandLineInterface.main(new String[] {PROVISION_COMMAND, "-h"});
    }

    @Test
    public void testShowProvisionUsageHelpWithoutHelpArg() {
        exit.checkAssertionAfterwards(() -> {
            assertEquals("Unexpected output from CLI for provisionconfig usage help",
                    PROVISION_USAGE,
                    systemOutRule.getLog());
            assertEquals("Missing error output",
                    "ERROR: Missing required parameters: CONFIGDIR, HOSTNAME, COMMON_OR_CSRFILE_NAME"
                            + System.lineSeparator(),
                    systemErrRule.getLog());
        });
        // With no args
        CommandLineInterface.main(new String[] {PROVISION_COMMAND});
    }

    @Test
    public void testShowGenerateCsrUsageHelp() {
        exit.checkAssertionAfterwards(() -> assertEquals(
                "Unexpected output from CLI for generatecsr usage help",
                GENERATE_CSR_USAGE,
                systemOutRule.getLog()));
        // With -h arg
        CommandLineInterface.main(new String[] {GENERATE_CSR_COMMAND, "-h"});
    }

    @Test
    public void testShowGenerateCsrUsageHelpWithoutHelpArg() {
        exit.checkAssertionAfterwards(() -> {
            assertEquals("Unexpected output from CLI for generatecsr usage help",
                    GENERATE_CSR_USAGE,
                    systemOutRule.getLog());
            assertEquals("Missing error output",
                    "ERROR: Missing required parameters: CONFIGDIR, COMMON_NAME"
                            + System.lineSeparator(),
                    systemErrRule.getLog());
        });
        // With no args
        CommandLineInterface.main(new String[] {GENERATE_CSR_COMMAND});
    }

    @Test
    public void testShowUpdateConfigUsageHelp() {
        exit.checkAssertionAfterwards(() -> assertEquals(
                "Unexpected output from CLI for generatecsr usage help",
                UPDATE_CONFIG_USAGE,
                systemOutRule.getLog()));
        // With -h arg
        CommandLineInterface.main(new String[] {UPDATE_CONFIG_COMMAND, "-h"});
    }

    @Test
    public void testShowUpdateConfigUsageHelpWithoutHelpArg() {
        exit.checkAssertionAfterwards(() -> {
            assertEquals("Unexpected output from CLI for generatecsr usage help",
                    UPDATE_CONFIG_USAGE,
                    systemOutRule.getLog());
            assertEquals("Missing error output",
                    "ERROR: Missing required parameters: CONFIGDIR, HOSTNAME"
                            + System.lineSeparator(),
                    systemErrRule.getLog());
        });
        // With no args
        CommandLineInterface.main(new String[] {UPDATE_CONFIG_COMMAND});
    }

    private void testGenerateCSR(String[] commandLineArgs, String filePrefix,
                                 Map<ASN1ObjectIdentifier, String> certificateAttributes,
                                 List<String> csrSanValues) {
        exit.checkAssertionAfterwards(() -> {

            File privateKeyFile = new File(TEMP_FILE_DIR_NAME + File.separatorChar + filePrefix + ".key");
            File csrFile = new File(TEMP_FILE_DIR_NAME + File.separatorChar + filePrefix + ".csr");

            try (FileInputStream csrFileInputStream = new FileInputStream(csrFile);
                 InputStreamReader csrInputStreamReader = new InputStreamReader(csrFileInputStream);
                 PemReader csrPemReader = new PemReader(csrInputStreamReader);
                 FileInputStream privateKeyFileInputStream = new FileInputStream(privateKeyFile);
                 InputStreamReader privateKeyInputStreamReader = new InputStreamReader(privateKeyFileInputStream);
                 PEMParser privateKeyPemParser = new PEMParser(privateKeyInputStreamReader)) {

                // Validate private key file
                assertTrue("The private key file does not exist", privateKeyFile.exists());
                PemObject privateKeyPemObject = privateKeyPemParser.readPemObject();
                JcaPEMKeyConverter jcaPEMKeyConverter = new JcaPEMKeyConverter();
                PrivateKey privateKey = jcaPEMKeyConverter.getPrivateKey(
                        PrivateKeyInfo.getInstance(ASN1Primitive.fromByteArray(privateKeyPemObject.getContent())));
                assertEquals("Private Key algorithm is not RSA.", "RSA", privateKey.getAlgorithm());

                // Validate CSR file
                assertTrue("The CSR file does not exist", csrFile.exists());
                PemObject csrPemObject = csrPemReader.readPemObject();
                final PKCS10CertificationRequest csr = new PKCS10CertificationRequest(csrPemObject.getContent());

                X500NameBuilder nameBuilder = new X500NameBuilder(X500Name.getDefaultStyle());
                for (Map.Entry<ASN1ObjectIdentifier, String> certificateAttribute : certificateAttributes.entrySet()) {
                    nameBuilder.addRDN(certificateAttribute.getKey(), certificateAttribute.getValue());
                }

                // Verify CSR subject name
                assertEquals("The CSR subject name is incorrect", nameBuilder.build(), csr.getSubject());

                if (csrSanValues != null && !csrSanValues.isEmpty()) {
                    Attribute[] csrAttributes = csr.getAttributes(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest);
                    if (csrAttributes != null && csrAttributes.length > 0) {
                        Extensions extensions = Extensions.getInstance(csrAttributes[0].getAttributeValues()[0]);
                        GeneralNames generalNames =
                                GeneralNames.fromExtensions(extensions, Extension.subjectAlternativeName);

                        for (GeneralName generalName : generalNames.getNames()) {
                            if (generalName.getTagNo() == GeneralName.dNSName) {
                                ASN1Encodable value = generalName.getName();
                                if (value instanceof ASN1String) {
                                    assertTrue("SAN value not in expected list",
                                            csrSanValues.contains(((ASN1String) value).getString()));
                                }
                            }
                        }
                    }
                }
            } finally {
                FileUtils.deleteDirectory(new File(TEMP_FILE_DIR_NAME));
            }
        });
        CommandLineInterface.main(commandLineArgs);
    }

    @Test
    public void testGenerateCsrBasic() {

        testGenerateCSR(new String[] {GENERATE_CSR_COMMAND, TEMP_FILE_DIR_NAME, CSR_COMMON_NAME}, DEFAULT_FILE_PREFIX,
                Collections.singletonMap(BCStyle.CN, CSR_COMMON_NAME), Collections.EMPTY_LIST);
    }

    @Test
    public void testGenerateCsrBasicWithArgs() {
        final String commonName = "myclient";
        final String filePrefix = "theclient";
        final String countyCode = "US";
        final String state = "OR";
        final String locality = "Hillsboro";
        final String organization = "McAfee";
        final String organizationalUnit = "DXL Teeam";
        final String email = "test@testm.com";
        final String san1 = "client1.myorg.com";
        final String san2 = "client1.myorg.net";

        Map<ASN1ObjectIdentifier, String> certificateOIDs = new HashMap<>();
        certificateOIDs.put(BCStyle.CN, commonName);
        certificateOIDs.put(BCStyle.C, countyCode);
        certificateOIDs.put(BCStyle.EmailAddress, email);
        certificateOIDs.put(BCStyle.L, locality);
        certificateOIDs.put(BCStyle.ST, state);
        certificateOIDs.put(BCStyle.O, organization);
        certificateOIDs.put(BCStyle.OU, organizationalUnit);

        List<String> csrSanValues = new ArrayList<>();
        csrSanValues.add(san1);
        csrSanValues.add(san2);

        testGenerateCSR(new String[] {GENERATE_CSR_COMMAND, TEMP_FILE_DIR_NAME, commonName, "-f", filePrefix,
                        "--country", countyCode, "--state-or-province", state, "--locality", locality, "--organization",
                        organization, "--organizational-unit", organizationalUnit, "--email-address", email,
                        "-s", san1, san2},
                filePrefix, certificateOIDs, csrSanValues);
    }
}
