/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client.cli;

import picocli.CommandLine;

/**
 * Subcommand abstract class for all CLI subcommands
 */
abstract class Subcommand {

    /**
     * Execution entry point for the subcommand. This method is called when the name of the implementing subcommand
     * is entered in the CLI arguments.
     *
     * @param parseResult The input CLI arguments
     */
    void execute(CommandLine.ParseResult parseResult) {
        // Do nothing
    }
}
