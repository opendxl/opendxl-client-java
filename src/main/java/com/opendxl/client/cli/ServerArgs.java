package com.opendxl.client.cli;

import org.apache.commons.lang3.StringUtils;
import picocli.CommandLine;

import java.io.IOException;

public class ServerArgs {

//    @CommandLine.Parameters(index = "${sys:hostNameParamIndex}", paramLabel = "HOSTNAME",
//            description = "Hostname where the management service resides")
//    private String hostName;

    @CommandLine.Option(names = {"-u", "--user"}, paramLabel = "USERNAME",
            description = "User registered at the management service")
    private String user;

    @CommandLine.Option(names = {"-p", "--password"}, paramLabel = "PASSWORD",
            description = "Password for the management service user")
    private String password;

    @CommandLine.Option(names = {"-t", "--port"}, paramLabel = "PORT",
            description = "Port where the management service resides", defaultValue = "8443")
    private int port;

    @CommandLine.Option(names = {"-e", "--truststore"}, paramLabel = "TRUSTSTORE_FILE",
            description = "Name of file containing one or more CA pems to use in validating the management server",
            defaultValue = "")
    private String trustStoreFile;

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getTrustStoreFile() {
        return trustStoreFile;
    }

    public void setTrustStoreFile(String trustStoreFile) {
        this.trustStoreFile = trustStoreFile;
    }

    public void promptServerArgs() throws IOException {
        if (StringUtils.isBlank(user)) {
            this.user = CommandLineInterface.getValueFromPrompt("server username", false);
        }

        if (StringUtils.isBlank(password)) {
            this.password = CommandLineInterface.getValueFromPrompt("server password", false);
        }
    }


}
