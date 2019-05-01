/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client.cli;

import com.opendxl.client.Broker;
import com.opendxl.client.DxlClientConfig;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.message.BasicNameValuePair;
import org.apache.log4j.Logger;
import org.bouncycastle.operator.OperatorCreationException;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * CLI command for provisioning a DXL client.
 * <p>
 * This command performs the following steps:
 * </p>
 * <ul>
 * <li>
 * Either generates a certificate signing request and private key, storing
 * each to a file, (the default) or reads the certificate signing request
 * from a file (if the "-r" argument is specified).
 * </li>
 * <li>
 * Sends the certificate signing request to a signing endpoint on a
 * management server. The HTTP response payload for this request should look
 * like the following:
 * </li>
 * </ul>
 * <br>
 * <pre>
 *     OK:
 *     "[ca bundle],[signed client cert],[broker config]"
 * </pre>
 * <p>
 * Sections of the response include:
 * </p>
 * <ul>
 * <li>
 * A line with the text "OK:" if the request was successful, else
 * "ERROR &lt;code&gt;:" on failure.
 * </li>
 * <li>A JSON-encoded string with a double-quote character at the beginning
 * and end and with the following parts, comma-delimited:
 * <ul>
 * <li>
 * [ca bundle] - a concatenation of one or more PEM-encoded CA
 * certificates
 * </li>
 * <li>
 * [signed client cert] - a PEM-encoded certificate signed from the
 * certificate request
 * </li>
 * <li>[broker config] - zero or more lines, each delimited by a line feed
 * character, for each of the brokers known to the management service.
 * Each line contains a key and value, delimited by an equal sign. The
 * key contains a broker guid. The value contains other metadata for the
 * broker, e.g., the broker guid, port, hostname, and ip address. For
 * example: "[guid1]=[guid1];8883;broker;10.10.1.1\n[guid2]=[guid2]...".
 * </li>
 * </ul>
 * </li>
 * <li>
 * Saves the [ca bundle] and [signed client cert] to separate files.
 * </li>
 * <li>
 * Creates a "dxlclient.config" file with the following sections:
 * <ul>
 * <li>
 * A "Certs" section with certificate configuration which refers to the
 * locations of the private key, ca bundle, and certificate files.
 * </li>
 * <li>
 * A "Brokers" section with the content of the [broker config] provided
 * by the management service.
 * </li>
 * </ul>
 * </li>
 * </ul>
 * <p>
 * To invoke this CLI command, the first argument must be <i>provisionconfig</i>. For example:
 * </p>
 * <pre>
 *     $&gt; java -jar dxlclient-0.1.0-all.jar provisionconfig ...
 * </pre>
 * <p>
 * The provision DXL Client command requires three CLI arguments:
 * </p>
 * <ul>
 * <li>
 * CONFIGDIR - The path to the configuration directory
 * </li>
 * <li>
 * HOSTNAME - The hostname where the management service resides
 * </li>
 * <li>
 * COMMON_OR_CSRFILE_NAME - The Common Name (CN) in the Subject DN for a new csr or the filename for a
 * pre-existing csr if the <i>-r</i> option is also used as CLI argument
 * </li>
 * </ul>
 * <p>
 * An example usage of this command is the following:
 * </p>
 * <pre>
 *     $&gt; java -jar dxlclient-0.1.0-all.jar provisionconfig config myserver dxlclient1
 * </pre>
 */
@CommandLine.Command(name = "provisionconfig", description = "Download and provision the DXL client configuration",
        mixinStandardHelpOptions = true)
class ProvisionDxlClientSubcommand extends DxlCliCommand {

    /**
     * The logger
     */
    private static Logger logger = Logger.getLogger(MethodHandles.lookup().lookupClass());

    /**
     * The DXL Client provision command
     */
    private static final String PROVISION_COMMAND = "DxlBrokerMgmt.generateOpenDXLClientProvisioningPackageCmd";
    /**
     * The CSR string parameter name
     */
    private static final String CSR_STRING_PARAMETER_NAME = "csrString";
    /**
     * The delimiter separating different data in the results of the provision command
     */
    private static final String PROVISION_COMMAND_RESULTS_DELIMITER = ",";

//    @CommandLine.Mixin
//    private ConfigDirArg configDirArg;
    /**
     * The path to the config directory
     */
    @CommandLine.Parameters(index = "0", paramLabel = "CONFIGDIR",
            description = "Path to the config directory")
    private String configDir;

    /**
     * The hostname where the management service resides
     */
    @CommandLine.Parameters(index = "1", paramLabel = "HOSTNAME",
            description = "Hostname where the management service resides")
    private String hostName;

    /**
     * The common name for a new CSR or the CSR file name
     */
    @CommandLine.Parameters(index = "2", paramLabel = "COMMON_OR_CSRFILE_NAME", defaultValue = "",
            description = "If \"-r\" is specified, interpret as the filename for a pre-existing csr. If \"-r\" is not "
                    + "specified, use as the Common Name (CN) in the Subject DN for a new csr.")
    private String commonOrCsrFileName;

    /**
     * The cryptography related CLI arguments
     */
    @CommandLine.Mixin
    private CryptoArgs cryptoArgs;

    /**
     * The management service related CLI arguments
     */
    @CommandLine.Mixin
    private ServerArgs serverArgs;

    /**
     * The CLI option indicating if the COMMON_OR_CSRFILE_NAME should be interpreted as the filename of an existing
     * CSR to be signed
     */
    @CommandLine.Option(names = {"-r", "--cert-request-file"},
            description = "Interpret COMMON_OR_CSRFILE_NAME as a filename for an existing csr to be signed. If not "
                    + "specified, a new csr is generated.", defaultValue = "")
    private String certRequestFile;

    /**
     * Method that either reads an existing CSR or generates a new CSR
     * <p>
     *
     * @return A CSR in PEM format as a string
     * @throws IOException                        If there is an issue reading or writing a CSR
     * @throws InvalidAlgorithmParameterException If there is an issue generating the private key
     * @throws NoSuchAlgorithmException           If there is an issue generating the private key
     * @throws OperatorCreationException          If there is an issue generating the key pair or CSR
     */
    private String processCsrAndPrivateKey() throws IOException, InvalidAlgorithmParameterException,
            NoSuchAlgorithmException, OperatorCreationException {
        if (StringUtils.isNotBlank(this.certRequestFile)) {
            return new String(Files.readAllBytes(Paths.get(certRequestFile)));
        } else {
            CsrAndPrivateKeyGenerator csrAndPrivateKeyGenerator =
                    new CsrAndPrivateKeyGenerator(this.commonOrCsrFileName, this.cryptoArgs);
            csrAndPrivateKeyGenerator.saveCsrAndPrivateKey(this.configDir);

            return csrAndPrivateKeyGenerator.getCsrAsPemString();
        }
    }

    /**
     * Method to process the list of broker returned from invoking the provision command on the Management Service
     * and return a list of {@link Broker} objects.
     *
     * @param brokers A string containing the list of brokers separated by the new line character
     * @return A list of {@link Broker} objects.
     * @throws Exception If there is an issue with the brokers String passed in to this method
     */
    private List<Broker> brokersForConfig(String brokers) throws Exception {
        List<Broker> brokersList = new ArrayList<>();

        List<String> brokerLines = Arrays.asList(brokers.split("\\R"));
        for (String brokerLine : brokerLines) {
            String[] brokerKeyPairAsArray = brokerLine.split(CommandLineInterface.KEY_VALUE_PAIR_SPLITTER);
            if (brokerKeyPairAsArray.length < 2) {
                throw new Exception("Invalid key value pair for broker entry: " + brokerLine);
            }

            Broker broker = Broker.parse(brokerKeyPairAsArray[1]);

            if (StringUtils.isBlank(broker.getUniqueId())) {
                throw new Exception("No guid for broker: " + brokerKeyPairAsArray[1]);
            }

            if (!broker.getUniqueId().equals(brokerKeyPairAsArray[0])) {
                throw new Exception(
                        String.format("guid for broker key %s did not match guid for broker value: %s. "
                                + "Broker line: %s", brokerKeyPairAsArray[0], broker.getUniqueId(), brokerLine));
            }

            brokersList.add(broker);
        }

        return brokersList;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute() {

        try {
            // Get server username and pass if missing
            serverArgs.promptServerArgs();

            // Create and save CSR and private key
            String csrAsString = processCsrAndPrivateKey();

            // Get the CSR signed by the management service
            ManagementService managementService = new ManagementService(this.hostName, this.serverArgs.getPort(),
                    this.serverArgs.getUser(), this.serverArgs.getPassword(), this.serverArgs.getTrustStoreFile());
            String provisionCommandResults = managementService.invokeCommand(PROVISION_COMMAND,
                    Collections.singletonList(new BasicNameValuePair(CSR_STRING_PARAMETER_NAME, csrAsString)));

            String[] provisionCommandResultsArray = provisionCommandResults.split(PROVISION_COMMAND_RESULTS_DELIMITER);

            if (provisionCommandResultsArray.length < 3) {
                throw new IOException(
                        String.format("Did not receive expected number of response elements. Expected 3, "
                                        + "Received %d. Value: %s", provisionCommandResultsArray.length,
                                provisionCommandResults));
            }

            // Create brokers list
            List<Broker> brokers = brokersForConfig(provisionCommandResultsArray[2]);

            // Create web socket brokers list
            List<Broker> websocketBrokers = new ArrayList<>();
            if (provisionCommandResultsArray.length > 3) {
                websocketBrokers = brokersForConfig(provisionCommandResultsArray[3]);
            }

            String certFileName = this.cryptoArgs.getFilePrefix() + ".crt";
            String certKeyName = this.cryptoArgs.getFilePrefix() + ".key";

            // Create DxlClientConfig object
            DxlClientConfig dxlClientConfig = new DxlClientConfig(CommandLineInterface.CA_BUNDLE_FILE_NAME,
                    certFileName, certKeyName, brokers, websocketBrokers);

            // create config dir if it does not exist
            File configDirFile = new File(this.configDir);
            if (!configDirFile.exists()) {
                configDirFile.mkdirs();
            }

            // Write DxlClientConfig as a config file to disk
            String dxlClientCongigPath = this.configDir + File.separatorChar
                    + CommandLineInterface.DXL_CONFIG_FILE_NAME;
            logger.info("Saving DXL config file to " + dxlClientCongigPath);
            dxlClientConfig.write(dxlClientCongigPath);

            String brokerCaBundlePath = this.configDir + File.separatorChar + CommandLineInterface.CA_BUNDLE_FILE_NAME;
            String certFilePath = configDir + File.separatorChar + certFileName;
            // Save CA bundle
            CertUtils.writePemFile(brokerCaBundlePath, "ca bundle", provisionCommandResultsArray[0]);
            // Save client certificate
            CertUtils.writePemFile(certFilePath, "client certificate", provisionCommandResultsArray[1]);
        } catch (Exception ex) {
            logger.error("Error while provisioning DXL Client.", ex);
        }
    }
}
