package com.opendxl.client.cli;

import com.opendxl.client.Broker;
import com.opendxl.client.DxlClientConfig;
import com.opendxl.client.cli.certs.CertUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.message.BasicNameValuePair;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.util.io.pem.PemObject;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@CommandLine.Command(name = "provisionconfig", description = "Download and provision the DXL client configuration",
        mixinStandardHelpOptions = true)
public class ProvisionDxlClientSubcommand implements Subcommand {

    private static final String PROVISION_COMMAND = "DxlBrokerMgmt.generateOpenDXLClientProvisioningPackageCmd";
    private static final String CSR_STRING_PARAMETER_NAME = "csrString";
    private static final String PROVISION_COMMAND_RESULTS_DELIMITER = ",";
    private static final String KEY_VALUE_PAIR_SPLITTER = "=";
    private static final String CA_BUNDLE_FILE_NAME = "ca-bundle.crt";
    private static final String DXL_CONFIG_FILE_NAME = "dxlclient.config";

    //    @CommandLine.Mixin
//    private ConfigDirArg configDirArg;
    @CommandLine.Parameters(index = "0", paramLabel = "CONFIGDIR",
            description = "Path to the config directory")
    private String configDir;

    @CommandLine.Parameters(index = "1", paramLabel = "HOSTNAME",
            description = "Hostname where the management service resides")
    private String hostName;

    @CommandLine.Mixin
    private CryptoArgs cryptoArgs;

    @CommandLine.Mixin
    private ServerArgs serverArgs;

    @CommandLine.Parameters(index = "2", paramLabel = "COMMON_OR_CSRFILE_NAME",
            description = "If \"-r\" is specified, interpret as the filename for a pre-existing csr. If \"-r\" is not "
                    + "specified, use as the Common Name (CN) in the Subject DN for a new csr.")
    private String commonOrCsrFileName;

    @CommandLine.Option(names = {"-r", "--cert-request-file"},
            description = "Interpret COMMON_OR_CSRFILE_NAME as a filename for an existing csr to be signed. If not "
                    + "specified, a new csr is generated.", defaultValue = "")
    private String certRequestFile;

    private String processCsrAndPrivateKey() throws IOException, InvalidAlgorithmParameterException,
            NoSuchAlgorithmException, OperatorCreationException {
        if (StringUtils.isNotBlank(this.certRequestFile)) {
            return new String(Files.readAllBytes(Paths.get(commonOrCsrFileName)));
        } else {
            return this.cryptoArgs.generateCsrAndPrivateKey(this.configDir, this.commonOrCsrFileName);
        }
    }

    @Override
    public void execute(CommandLine.ParseResult parseResult) {

        try {
            // Get server username and pass if missing
            serverArgs.promptServerArgs();

            // Create and save CSR and private key
            String csrAsString = processCsrAndPrivateKey();
            System.out.println("Saved CSR and private key");

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

            String brokerCaBundlePath = this.configDir + File.separatorChar + CA_BUNDLE_FILE_NAME;

            // Write config file
            DxlClientConfig dxlClientConfig = new DxlClientConfig(brokerCaBundlePath,
                    this.cryptoArgs.certFileName(this.configDir),
                    this.cryptoArgs.privateKeyFileName(this.configDir), brokers);

            dxlClientConfig.write(this.configDir + File.separatorChar + DXL_CONFIG_FILE_NAME);

            // Save CA bundle
            CertUtils.writePemFile(brokerCaBundlePath,
                    new PemObject(CertUtils.CERTIFICATE_OBJECT_TYPE_STRING, provisionCommandResultsArray[0].getBytes()),
                    null);
            // Save client certificate
            CertUtils.writePemFile(this.cryptoArgs.certFileName(this.configDir),
                    new PemObject(CertUtils.CERTIFICATE_OBJECT_TYPE_STRING, provisionCommandResultsArray[1].getBytes()),
                    null);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private List<Broker> brokersForConfig(String brokers) throws Exception {
        List<Broker> brokersList = new ArrayList<>();

        List<String> brokerLines = Arrays.asList(brokers.split("\\R"));
        for (String brokerLine : brokerLines) {
            String[] brokerKeyPairAsArray = brokerLine.split(KEY_VALUE_PAIR_SPLITTER);
            if (brokerKeyPairAsArray.length < 2) {
                throw new Exception("Invalid key value pair for broker entry: " + brokerLine);
            }

            Broker broker = Broker.parseFromConfigString(brokerKeyPairAsArray[1]);

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
}
