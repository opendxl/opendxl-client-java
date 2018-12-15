package com.opendxl.client.cli;

import org.apache.log4j.Logger;
import picocli.CommandLine;

import java.lang.invoke.MethodHandles;

/**
 * Subcommand for generating a certificate signing request and private key and storing each to a file.
 */
@CommandLine.Command(name = "generatecsr", description = "Generate CSR and private key",
        mixinStandardHelpOptions = true)
class GenerateCsrAndPrivateKeySubcommand implements Subcommand {

    /**
     * The logger
     */
    private static Logger logger = Logger.getLogger(MethodHandles.lookup().lookupClass());

//    @CommandLine.Mixin
//    private ConfigDirArg configDirArg;
    /**
     * The path to the config directory
     */
    @CommandLine.Parameters(index = "0", paramLabel = "CONFIGDIR",
            description = "Path to the config directory")
    private String configDir;

    /**
     * The common name for a new CSR or the CSR file name
     */
    @CommandLine.Parameters(index = "1", paramLabel = "COMMON_NAME",
            description = "Common Name (CN) to use in the CSR's Subject DN")
    private String commonName;

    /**
     * The cryptography related CLI arguments
     */
    @CommandLine.Mixin
    private CryptoArgs cryptoArgs;

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(CommandLine.ParseResult parseResult) {
        try {
            this.cryptoArgs.generateCsrAndPrivateKey(this.configDir, this.commonName);
        } catch (Exception e) {
            logger.error("Error creating private key and CSR.", e);
        }
    }
}
