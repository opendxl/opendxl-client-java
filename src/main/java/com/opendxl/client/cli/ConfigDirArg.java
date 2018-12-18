/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client.cli;

import picocli.CommandLine;

/**
 * The configuration directory argument class for CLI subcommands that require a configuration directory.
 */
class ConfigDirArg {

    /**
     * The configuration directory
     */
    @CommandLine.Parameters(index = "${sys:configdirParamIndex}", paramLabel = "CONFIGDIR",
            description = "Path to the config directory")
    private String configDir;

    /**
     * Get the configuration directory
     *
     * @return The configuration directory
     */
    String getConfigDir() {
        return configDir;
    }

    /**
     * Set the configuration directory
     *
     * @param configDir The configuration directory
     */
    void setConfigDir(String configDir) {
        this.configDir = configDir;
    }
}
