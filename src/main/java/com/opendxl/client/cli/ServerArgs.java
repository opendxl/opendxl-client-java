package com.opendxl.client.cli;

import org.apache.commons.lang3.StringUtils;
import picocli.CommandLine;

import java.io.IOException;

/**
 * Class containing members used for cli subcommands which communicating with a server require,
 * e.g., hostname and credential information.
 */
class ServerArgs {

//    @CommandLine.Parameters(index = "${sys:hostNameParamIndex}", paramLabel = "HOSTNAME",
//            description = "Hostname where the management service resides")
//    private String hostName;

    /**
     * User registered at the management service
     */
    @CommandLine.Option(names = {"-u", "--user"}, paramLabel = "USERNAME",
            description = "User registered at the management service")
    private String user;

    /**
     * Password for the management service user
     */
    @CommandLine.Option(names = {"-p", "--password"}, paramLabel = "PASSWORD",
            description = "Password for the management service user")
    private String password;

    /**
     * Port where the management service resides
     */
    @CommandLine.Option(names = {"-t", "--port"}, paramLabel = "PORT",
            description = "Port where the management service resides", defaultValue = "8443")
    private int port;

    /**
     * Name of file containing one or more CA pems to use in validating the management server
     */
    @CommandLine.Option(names = {"-e", "--truststore"}, paramLabel = "TRUSTSTORE_FILE",
            description = "Name of file containing one or more CA pems to use in validating the management server",
            defaultValue = "")
    private String trustStoreFile;

    /**
     * Get the user registered at the management service
     *
     * @return The user registered at the management service
     */
    String getUser() {
        return user;
    }

    /**
     * Set the user registered at the management service
     *
     * @param user The user registered at the management service
     */
    void setUser(String user) {
        this.user = user;
    }

    /**
     * Get the password for the management service user
     *
     * @return The password for the management service user
     */
    String getPassword() {
        return password;
    }

    /**
     * Set the password for the management service user
     *
     * @param password The password for the management service user
     */
    void setPassword(String password) {
        this.password = password;
    }

    /**
     * Get the port where the management service resides
     *
     * @return The port where the management service resides
     */
    int getPort() {
        return port;
    }

    /**
     * Set the port where the management service resides
     *
     * @param port The port where the management service resides
     */
    void setPort(int port) {
        this.port = port;
    }

    /**
     * Get the name of file containing one or more CA pems to use in validating the management server
     *
     * @return The name of file containing one or more CA pems to use in validating the management server
     */
    String getTrustStoreFile() {
        return trustStoreFile;
    }

    /**
     * Set the name of file containing one or more CA pems to use in validating the management server
     *
     * @param trustStoreFile The name of file containing one or more CA pems to use in validating the management server
     */
    void setTrustStoreFile(String trustStoreFile) {
        this.trustStoreFile = trustStoreFile;
    }

    /**
     * Method to prompt the user for the username and password if they were not passed as parameters initially
     *
     * @throws IOException If there is an issue getting data from the CLI
     */
    void promptServerArgs() throws IOException {
        if (StringUtils.isBlank(user)) {
            this.user = CommandLineInterface.getValueFromPrompt("server username", false);
        }

        if (StringUtils.isBlank(password)) {
            this.password = CommandLineInterface.getValueFromPrompt("server password", false);
        }
    }
}
