/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client.cli;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opendxl.client.Broker;
import com.opendxl.client.DxlClientConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * CLI command for updating the DXL client configuration in the dxlclient.config file, specifically the
 * ca bundle and broker configuration.
 * <p>
 * This command performs the following steps:
 * </p>
 * <ul>
 * <li>
 * Sends a request to a management server endpoint for the latest ca bundle
 * information. The HTTP response payload for this request should look
 * like the following:
 * <br>
 * <pre>
 *     OK:
 *     "[ca bundle]"
 * </pre>
 * Sections of the response include:
 * <ul>
 * <li>
 * A line with the text "OK:" if the request was successful, else
 * "ERROR [code]:" on failure.
 * </li>
 * <li>
 * A JSON-encoded string with a double-quote character at the beginning
 * and end. The string contains a concatenation of one or more PEM-encoded
 * CA certificates.
 * </li>
 * </ul>
 * </li>
 * <li>
 * Saves the [ca bundle] to the file at the location specified in the
 * "BrokerCertChain" setting in the "Certs" section of the dxlclient.config
 * file.
 * </li>
 * <li>
 * Sends a request to a management server endpoint for the latest broker
 * configuration. The HTTP response payload for this request should look
 * like the following:
 * <pre>
 *     OK:
 *     "[broker config]"
 * </pre>
 * Sections of the response include:
 * <ul>
 * <li>
 * A line with the text "OK:" if the request was successful, else
 * "ERROR [code]:" on failure.
 * </li>
 * <li>
 * A JSON-encoded string with a double-quote character at the beginning
 * and end. The string should contain a JSON document which looks similar
 * to the following:
 * <pre>
 *     {
 *         "brokers": [
 *             {
 *                 "guid": "{2c5b107c-7f51-11e7-0ebf-0800271cfa58}",
 *                 "hostName": "broker1",
 *                 "ipAddress": "10.10.100.100",
 *                 "port": 8883
 *             },
 *             {
 *                 "guid": "{e90335b2-8dc8-11e7-1bc3-0800270989e4}",
 *                 "hostName": "broker2",
 *                 "ipAddress": "10.10.100.101",
 *                 "port": 8883
 *             },
 *             ...
 *         ],
 *         "brokersWebSockets": [
 *             {
 *                 "guid": "{2c5b107c-7f51-11e7-0ebf-0800271cfa58}",
 *                 "hostName": "broker1",
 *                 "ipAddress": "10.10.100.100",
 *                 "port": 443
 *             },
 *             {
 *                 "guid": "{e90335b2-8dc8-11e7-1bc3-0800270989e4}",
 *                 "hostName": "broker2",
 *                 "ipAddress": "10.10.100.101",
 *                 "port": 443
 *             },
 *             ...
 *         ],
 *         "certVersion": 0
 *     }
 * </pre>
 * </li>
 * </ul>
 * </li>
 * <li>
 * Saves the [broker config] to the "Brokers" section of the
 * dxlclient.config file.
 * </li>
 * </ul>
 * <p>
 * Updates to the dxlclient.config file do not attempt to preserve comments in the
 * file. If a broker listed in the config file on disk is no longer known to the management server, the
 * broker's config entry and any comments directly above it are removed from
 * the config file.
 * <p>
 * An example usage of this command is the following:
 * </p>
 * <pre>
 *     $&gt; java -jar dxlclient-0.1.0-all.jar updateconfig config myserver
 * </pre>
 */
@CommandLine.Command(name = "updateconfig", description = "Update the DXL client configuration",
    mixinStandardHelpOptions = true)
class UpdateConfigSubcommand extends DxlCliCommand {

    /**
     * The logger
     */
    private static Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

    /**
     * The Broker Cert Chain command
     */
    private static final String BROKER_CERT_CHAIN_COMMAND = "DxlClientMgmt.createClientCaBundle";
    /**
     * The Broker list command
     */
    private static final String BROKER_LIST_COMMAND = "DxlClientMgmt.getBrokerList";

    /**
     * The basic Jackson JSON ObjectMapper used for deserializing the results of the broker list command.
     */
    private static final ObjectMapper GENERIC_MAPPER = new ObjectMapper();

    /**
     * The path to the config directory
     */
    @CommandLine.Parameters(index = "0", paramLabel = "CONFIGDIR",
        description = "Path to the config directory")
    private String configDir;

    /**
     * The hostname where the management service resides
     */
    @CommandLine.Parameters(index = "1", paramLabel = "HOSTNAME",
        description = "Hostname where the management service resides")
    private String hostName;

    /**
     * The management service related CLI arguments
     */
    @CommandLine.Mixin
    private ServerArgs serverArgs;

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute() {

        try {
            // Get server username and pass if missing
            serverArgs.promptServerArgs();

            // Make sure the config file exists
            File configFile = new File(this.configDir + File.separatorChar
                + CommandLineInterface.DXL_CONFIG_FILE_NAME);
            if (!configFile.exists()) {
                throw new Exception("Unable to find config file to update: " + configFile);
            }

            // Create a DxlClientConfig from the input configFile location
            DxlClientConfig dxlClientConfig = DxlClientConfig.createDxlConfigFromFile(configFile.getAbsolutePath());

            // Create a ManagementService instance for communicating with the Management Service
            ManagementService managementService = new ManagementService(this.hostName, this.serverArgs.getPort(),
                this.serverArgs.getUser(), this.serverArgs.getPassword(), this.serverArgs.getTrustStoreFile());

            // Invoke the broker cert chain command on the management service
            String brokerCertChainResponse = managementService.invokeCommand(BROKER_CERT_CHAIN_COMMAND,
                Collections.EMPTY_LIST);

            // Save broker cert chain response to disk
            logger.info("Updating certs in " + dxlClientConfig.getBrokerCaBundlePath());
            CertUtils.writePemFile(dxlClientConfig.getBrokerCaBundlePath(), brokerCertChainResponse);

            String brokerListCommandResultsAsString = managementService.invokeCommand(BROKER_LIST_COMMAND,
                Collections.EMPTY_LIST);

            BrokerListCommandResults brokerListCommandResults =
                GENERIC_MAPPER.readValue(brokerListCommandResultsAsString, BrokerListCommandResults.class);
            // Set MQTT broker list
            List<Broker> existingBrokers = dxlClientConfig.getBrokerList();
            existingBrokers.clear();
            existingBrokers.addAll(brokerListCommandResults.getBrokers());

            // Set WebSocket broker list
            List<Broker> existingWebSocketBrokers = dxlClientConfig.getWebSocketBrokers();
            existingWebSocketBrokers.clear();
            existingWebSocketBrokers.addAll(brokerListCommandResults.getWebSocketBrokers());

            logger.info("Updating DXL config file at " + configFile.getPath());
            dxlClientConfig.write(configFile.getAbsolutePath());
        } catch (Exception ex) {
            logger.error("Error attempting to the ca bundle and broker configurations.", ex);
        }
    }


    /**
     * Model object for the JSON returned by the broker list command
     */
    private static class BrokerListCommandResults {
        /**
         * The list of brokers supporting standard MQTT connections
         */
        private List<Broker> brokers;
        /**
         * The list of brokers supporting DXL connections over WebSockets
         */
        private List<Broker> webSocketBrokers = new ArrayList<>();
        /**
         * The certificate version
         */
        private int certVersion;

        /**
         * Get the list of brokers supporting standard MQTT connections
         *
         * @return The list of brokers supporting standard MQTT connections
         */
        List<Broker> getBrokers() {
            return brokers;
        }

        /**
         * Set the list of brokers supporting standard MQTT connections
         *
         * @param brokers The list of brokers supporting standard MQTT connections
         */
        void setBrokers(List<Broker> brokers) {
            this.brokers = brokers;
        }

        /**
         * Get the list of brokers supporting DXL connections over WebSockets
         *
         * @return The list of brokers supporting DXL connections over WebSockets
         */
        public List<Broker> getWebSocketBrokers() {
            return webSocketBrokers;
        }

        /**
         * Set the list of brokers supporting DXL connections over WebSockets
         *
         * @param webSocketBrokers The list of brokers supporting DXL connections over WebSockets
         */
        public void setWebSocketBrokers(List<Broker> webSocketBrokers) {
            this.webSocketBrokers = webSocketBrokers;
        }

        /**
         * Setter called by Jackson when deserializing the JSON results of the broker list command. This method
         * creates a Broker object and sets the protocol on the Broker object to be WSS for every Broker in the
         * results.
         *
         * @param fieldKey      The JSON field
         * @param embeddedField The deserialized JsonNode for the webSocketBrokers section of the result JSON
         */
        @JsonAnySetter
        public void setWebSocketBrokers(String fieldKey, JsonNode embeddedField) {
            embeddedField.forEach(jsonNode -> {
                try {
                    Broker broker = GENERIC_MAPPER.readValue(jsonNode.toString(), Broker.class);
                    broker.setProtocol(Broker.WSS_PROTOCOL);
                    webSocketBrokers.add(broker);
                } catch (IOException e) {
                    logger.warn("Error creating websocket broker", e);
                }
            });
        }

        /**
         * Get the certificate version
         *
         * @return The certificate version
         */
        int getCertVersion() {
            return certVersion;
        }

        /**
         * Set the certificate version
         *
         * @param certVersion The certificate version
         */
        void setCertVersion(int certVersion) {
            this.certVersion = certVersion;
        }
    }
}


