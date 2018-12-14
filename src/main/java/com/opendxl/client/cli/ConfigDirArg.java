package com.opendxl.client.cli;

import picocli.CommandLine;

/**
 * The configuration directory argument class for CLI subcommands that require a configuration directory.
 */
public class ConfigDirArg {

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
    public String getConfigDir() {
        return configDir;
    }

    /**
     * Set the configuration directory
     *
     * @param configDir The configuration directory
     */
    public void setConfigDir(String configDir) {
        this.configDir = configDir;
    }
}
