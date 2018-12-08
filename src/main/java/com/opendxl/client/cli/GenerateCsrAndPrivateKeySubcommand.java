package com.opendxl.client.cli;

import picocli.CommandLine;

@CommandLine.Command(name = "generatecsr", description = "Generate CSR and private key",
        mixinStandardHelpOptions = true)
public class GenerateCsrAndPrivateKeySubcommand implements Subcommand {

    @CommandLine.Mixin
    private ConfigDirArg configDirArg;

    @CommandLine.Mixin
    private CryptoArgs cryptoArgs;

    @CommandLine.Parameters(index = "0", paramLabel = "COMMON_NAME",
            description = "Common Name (CN) to use in the CSR's Subject DN")
    private String commonName;


    @Override
    public void execute(CommandLine.ParseResult parseResult) {

    }
}
