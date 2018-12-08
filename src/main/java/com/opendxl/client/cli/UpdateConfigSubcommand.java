package com.opendxl.client.cli;

import picocli.CommandLine;

@CommandLine.Command(name = "updateconfig", description = "Update the DXL client configuration",
        mixinStandardHelpOptions = true)
public class UpdateConfigSubcommand implements Subcommand {

    @CommandLine.Mixin
    private ConfigDirArg configDirArg;

    @CommandLine.Mixin
    private ServerArgs serverArgs;


    @Override
    public void execute(CommandLine.ParseResult parseResult) {

    }
}
