package com.opendxl.client.cli;

import picocli.CommandLine;

public interface Subcommand {
    void execute(CommandLine.ParseResult parseResult);
}
