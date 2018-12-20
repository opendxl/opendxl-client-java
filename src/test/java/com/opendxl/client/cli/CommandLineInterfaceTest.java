package com.opendxl.client.cli;

import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;

import java.util.Locale;

import static org.junit.Assert.assertEquals;


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
    private static final String GENERATE_CSR_USAGE = "";
    private static final String UPDATE_CONFIG_USAGE = "";

    private static final String PROVISION_COMMAND = "provisionconfig";
    private static final String GENERATE_CSR_COMMAND = "generatecsr";
    private static final String UPDATE_CONFIG_COMMAND = "updateconfig";


    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog();
    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog();
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
        exit.checkAssertionAfterwards(() -> assertEquals("Unexpected output from CLI for usage help",
                PROVISION_USAGE,
                systemOutRule.getLog()));
        // With -h arg
        CommandLineInterface.main(new String[] {PROVISION_COMMAND, "-h"});
    }

    @Test
    public void testShowProvisionUsageHelpWithoutHelpArg() {
        exit.checkAssertionAfterwards(() -> {
            assertEquals("Unexpected output from CLI for usage help",
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
}
