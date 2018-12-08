package com.opendxl.client.cli;

import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.operator.OperatorCreationException;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;

@CommandLine.Command(name = "provisionconfig", description = "Download and provision the DXL client configuration",
        mixinStandardHelpOptions = true)
public class ProvisionDxlClientSubcommand implements Subcommand {

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

            String csrAsString = processCsrAndPrivateKey();
            System.out.println("Saved CSR and private key");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
