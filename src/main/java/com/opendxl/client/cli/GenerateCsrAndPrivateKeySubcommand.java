/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client.cli;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;

import java.lang.invoke.MethodHandles;

/**
 * CLI command for generating a certificate signing request and private key, storing each to a file.
 * <p>
 * The provision DXL Client command requires three CLI arguments:
 * </p>
 * <ul>
 * <li>
 * CONFIGDIR - The path to the configuration directory where the CSR and private key will be saved
 * </li>
 * <li>
 * COMMON_NAME - The Common Name (CN) to use in the CSR's Subject DN
 * </li>
 * </ul>
 * <p>
 * An example usage of this command is the following:
 * </p>
 * <pre>
 *     $&gt; java -jar dxlclient-0.1.0-all.jar generatecsr config dxlclient1
 * </pre>
 */
@CommandLine.Command(name = "generatecsr", description = "Generate CSR and private key",
        mixinStandardHelpOptions = true)
class GenerateCsrAndPrivateKeySubcommand extends DxlCliCommand {

    /**
     * The logger
     */
    private static Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

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
    public void execute() {
        try {
            CsrAndPrivateKeyGenerator csrAndPrivateKeyGenerator =
                    new CsrAndPrivateKeyGenerator(this.commonName, this.cryptoArgs);
            csrAndPrivateKeyGenerator.saveCsrAndPrivateKey(this.configDir);
        } catch (Exception e) {
            logger.error("Error creating private key and CSR.", e);
        }
    }
}
