package com.opendxl.client.cli;

import picocli.CommandLine;

public class ConfigDirArg {
    @CommandLine.Parameters(index = "${sys:configdirParamIndex}", paramLabel = "CONFIGDIR",
            description = "Path to the config directory")
    private String configDir;

    public String getConfigDir() {
        return configDir;
    }

    public void setConfigDir(String configDir) {
        this.configDir = configDir;
    }
}
