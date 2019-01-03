/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client.cli;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.varia.LevelRangeFilter;
import picocli.CommandLine;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandles;
import java.net.URISyntaxException;

/**
 * Main class for the OpenDXL Java Client CLI commands.
 * <p>
 * There are three CLI commands for the OpenDXL Java Client:
 * </p>
 * <table style="border-spacing: 0px;">
 * <caption>List of CLI commands for OpenDXL Java Client</caption>
 * <tr style="background-color: #CCCCFF;">
 * <th id="commandName" style="text-align: left; white-space: nowrap; padding: 8px;">Command Name</th>
 * <th id="commandInfo" style="text-align: left; padding: 8px;">Command Information</th>
 * </tr>
 * <tr>
 * <th style="vertical-align: top; padding: 8px;">provisionconfig</th>
 * <td style="vertical-align: top; padding: 8px;">
 * This command is for provisioning a DXL Client and performs the following steps:
 * <ul>
 * <li>
 * Either generates a certificate signing request and private key, storing
 * each to a file, (the default) or reads the certificate signing request
 * from a file (if the "-r" argument is specified).
 * </li>
 * <li>
 * Sends the certificate signing request to a signing endpoint on a
 * management server. The HTTP response payload for this request should look
 * like the following:
 * </li>
 * </ul>
 * <br>
 * <pre>
 *     OK:
 *     "[ca bundle],[signed client cert],[broker config]"
 * </pre>
 * <p>
 * Sections of the response include:
 * </p>
 * <ul>
 * <li>
 * A line with the text "OK:" if the request was successful, else
 * "ERROR &lt;code&gt;:" on failure.
 * </li>
 * <li>A JSON-encoded string with a double-quote character at the beginning
 * and end and with the following parts, comma-delimited:
 * <ul>
 * <li>
 * [ca bundle] - a concatenation of one or more PEM-encoded CA
 * certificates
 * </li>
 * <li>
 * [signed client cert] - a PEM-encoded certificate signed from the
 * certificate request
 * </li>
 * <li>[broker config] - zero or more lines, each delimited by a line feed
 * character, for each of the brokers known to the management service.
 * Each line contains a key and value, delimited by an equal sign. The
 * key contains a broker guid. The value contains other metadata for the
 * broker, e.g., the broker guid, port, hostname, and ip address. For
 * example: "[guid1]=[guid1];8883;broker;10.10.1.1\n[guid2]=[guid2]...".
 * </li>
 * </ul>
 * </li>
 * <li>
 * Saves the [ca bundle] and [signed client cert] to separate files.
 * </li>
 * <li>
 * Creates a "dxlclient.config" file with the following sections:
 * <ul>
 * <li>
 * A "Certs" section with certificate configuration which refers to the
 * locations of the private key, ca bundle, and certificate files.
 * </li>
 * <li>
 * A "Brokers" section with the content of the [broker config] provided
 * by the management service.
 * </li>
 * </ul>
 * </li>
 * </ul>
 * <p>
 * To invoke this CLI command, the first argument must be <i>provisionconfig</i>. For example:
 * </p>
 * <pre>
 *     $&gt; java -jar dxlclient-0.1.0-all.jar provisionconfig ...
 * </pre>
 * <p>
 * The provision DXL Client command requires three CLI arguments:
 * </p>
 * <ul>
 * <li>
 * CONFIGDIR - The path to the configuration directory
 * </li>
 * <li>
 * HOSTNAME - The hostname where the management service resides
 * </li>
 * <li>
 * COMMON_OR_CSRFILE_NAME - The Common Name (CN) in the Subject DN for a new csr or the filename for a
 * pre-existing csr if the <i>-r</i> option is also used as CLI argument
 * </li>
 * </ul>
 * <p>
 * An example usage of this command is the following:
 * </p>
 * <pre>
 *     $&gt; java -jar dxlclient-0.1.0-all.jar provisionconfig config myserver dxlclient1
 * </pre>
 * </td>
 * </tr>
 * <tr>
 * <th style="vertical-align: top; padding: 8px;">updateconfig</th>
 * <td style="vertical-align: top; padding: 8px;">
 * This command is for updating the DXL client configuration in the dxlclient.config file, specifically the
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
 * </td>
 * </tr>
 * <tr>
 * <th style="vertical-align: top; padding: 8px;">generatecsr</th>
 * <td style="vertical-align: top; padding: 8px;">
 * This command is for generating a private key and CSR
 * <p>
 * The provision DXL Client command requires three CLI arguments:
 * </p>
 * <ul>
 * <li>
 * CONFIGDIR - The path to the configuration directory where the CSR and private key will be saved
 * </li>
 * <li>
 * COMMON_NAME - The Common Name (CN) to use in the CSR's Subject DN
 * </li>
 * </ul>
 * <p>
 * An example usage of this command is the following:
 * </p>
 * <pre>
 *     $&gt; java -jar dxlclient-0.1.0-all.jar generatecsr config dxlclient1
 * </pre>
 * </td>
 * </tr>
 * </table>
 */
@CommandLine.Command(name = "dxlclient", mixinStandardHelpOptions = true,
        subcommands = {GenerateCsrAndPrivateKeySubcommand.class,
                ProvisionDxlClientSubcommand.class, UpdateConfigSubcommand.class},
        versionProvider = CommandLineInterface.ManifestVersionProvider.class,
        helpCommand = true)
public class CommandLineInterface extends DxlCliCommand {

    /**
     * The logger
     */
    private static Logger logger = Logger.getLogger(MethodHandles.lookup().lookupClass());

    /**
     * The log4j logging pattern
     */
    private static final String LOGGING_PATTERN = "%5p: %m%n";

    /**
     * The key value pair splitter
     */
    static final String KEY_VALUE_PAIR_SPLITTER = "=";
    /**
     * The name of the certificate authority bundle file
     */
    static final String CA_BUNDLE_FILE_NAME = "ca-bundle.crt";
    /**
     * The name of the dxl client configuration file
     */
    static final String DXL_CONFIG_FILE_NAME = "dxlclient.config";
    /**
     * The command parameter list heading
     */
    static final String COMMAND_PARAMETER_LIST_HEADING = "\npositional arguments:\n";

    /**
     * The command options list heading
     */
    static final String COMMAND_OPTION_LIST_HEADING = "\noptional arguments:\n";

    /**
     * Private constructor
     */
    private CommandLineInterface() {
    }

    /**
     * Method to get a value from the command prompt
     *
     * @param title   The title of the value to be entered
     * @param confirm Whether to confirm the value or not
     * @return A string containing the value entered by the user
     * @throws IOException If there is an issue reading data input from the user
     */
    static String getValueFromPrompt(String title, boolean confirm) throws IOException {
        String value;
        while (true) {
            while (true) {
                value = String.valueOf(readPassword("Enter %s:", title));
                if (StringUtils.isBlank(value)) {
                    System.out.println("Value cannot be empty. Try again.");
                } else {
                    break;
                }
            }

            String confirmValue = confirm ? String.valueOf(readPassword("Confirm %s:", title)) : value;
            if (!value.equals(confirmValue)) {
                System.out.println("Values for " + title + " do not match. Try again.");
            } else {
                break;
            }
        }

        return value;
    }

    /**
     * Print a message to the console and read back the user input
     *
     * @param format The message to display on the command line
     * @param args   Any arguments to replace in the format string
     * @return The user's input for the displayed message
     * @throws IOException If there is an error displaying or reading data from the console
     */
    static String readLine(String format, Object... args) throws IOException {
        if (System.console() != null) {
            return System.console().readLine(format, args);
        }
        System.out.print(String.format(format, args));
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                System.in));
        return reader.readLine();
    }

    /**
     * Method to display a message to the console and return input from CLI. The user's input
     * will not be displayed or move the cursor.
     *
     * @param format The message to display on the command line
     * @param args   ny arguments to replace in the format string
     * @return Input from CLI
     * @throws IOException If there is an error displaying or reading data from the console
     */
    static char[] readPassword(String format, Object... args)
            throws IOException {
        if (System.console() != null)
            return System.console().readPassword(format, args);
        return readLine(format, args).toCharArray();
    }

    /**
     * Main entry point for the command line interface
     *
     * @param args The command line arguments
     */
    public static void main(String[] args) {
        // create info/debug log4j appender
        ConsoleAppender nonErrorConsole = new ConsoleAppender(new PatternLayout(LOGGING_PATTERN),
                ConsoleAppender.SYSTEM_OUT);
        LevelRangeFilter levelRangeFilter = new LevelRangeFilter();
        levelRangeFilter.setLevelMax(Level.INFO);
        levelRangeFilter.setLevelMin(Level.DEBUG);
        nonErrorConsole.addFilter(levelRangeFilter);

        // create error log4j appender
        ConsoleAppender errorConsole = new ConsoleAppender(new PatternLayout(LOGGING_PATTERN),
                ConsoleAppender.SYSTEM_ERR);
        errorConsole.setThreshold(Level.ERROR);

        // Remove all existing log4j appenders
        Logger.getRootLogger().removeAllAppenders();

        //add appender to any Logger (here is root)
        Logger.getRootLogger().addAppender(nonErrorConsole);
        Logger.getRootLogger().addAppender(errorConsole);

        // Set up the parser
        CommandLine commandLine = new CommandLine(new CommandLineInterface());
        try {
            String jarName = new File(
                    CommandLineInterface.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getName();
            if (!jarName.endsWith(".jar")) {
                jarName = "dxlclient-all.jar";
            }
            commandLine.setCommandName("java -jar " + jarName);
        } catch (URISyntaxException e) {
            logger.error("Error setting the usage name to be the name of the executing jar file", e);
            return;
        }

        // Get the command spec
        CommandLine.Model.CommandSpec commandSpec = commandLine.getCommandSpec();
        // Collect any errors from the root command instead of throwing them
        commandSpec.parser().collectErrors(true);
        // Collect any errors in subcommands instead of throwing them
        commandLine.getSubcommands().forEach((k, v) -> v.getCommandSpec().parser().collectErrors(true));

        // Set the parameter list and option list heading values for all commands
        commandSpec.usageMessage().parameterListHeading(COMMAND_PARAMETER_LIST_HEADING);
        commandSpec.usageMessage().optionListHeading(COMMAND_OPTION_LIST_HEADING);
        commandLine.getSubcommands().forEach((k, v) ->
                v.getCommandSpec().usageMessage().parameterListHeading(COMMAND_PARAMETER_LIST_HEADING));
        commandLine.getSubcommands().forEach((k, v) ->
                v.getCommandSpec().usageMessage().optionListHeading(COMMAND_OPTION_LIST_HEADING));

        CommandLine parser = new CommandLine(commandSpec);
        CommandLine.ParseResult rootParseResult = parser.parseArgs(args);

        // Set parseResult to the subcommand if one was specified
        CommandLine.ParseResult parseResult;
        if (rootParseResult.hasSubcommand()) {
            parseResult = rootParseResult.subcommand();
        } else {
            parseResult = rootParseResult;
        }

        // Show version info
        if (parseResult.isVersionHelpRequested()) {
            rootParseResult.commandSpec().commandLine().printVersionHelp(System.out);
            return;
        }

        // Get the DXL ClI command object
        DxlCliCommand parsedCommand = parseResult.commandSpec().commandLine().getCommand();
        // set the log level on non error console
        nonErrorConsole.setThreshold(parsedCommand.isVerbose() ? Level.DEBUG : Level.INFO);

        // show help message
        if (parseResult.isUsageHelpRequested() || !parseResult.errors().isEmpty() || (!parseResult.hasSubcommand()
                && parseResult.matchedOptions().isEmpty()
                && parseResult.matchedPositionals().isEmpty())) {
            parseResult.commandSpec().commandLine().usage(System.out);
            // Exit if there are no errors
            if (parseResult.errors().isEmpty()) {
                return;
            }
        }

        // show first error message
        if (!parseResult.errors().isEmpty()) {
            logger.error(parseResult.errors().get(0).getMessage());
            return;
        }

        // Execute the command
        parsedCommand.execute();
    }

    /**
     * {@link CommandLine.IVersionProvider} implementation that returns version information from the
     * dxlclient.jar file's {@code /META-INF/MANIFEST.MF} file.
     */
    static class ManifestVersionProvider implements CommandLine.IVersionProvider {
        /**
         * {@inheritDoc}
         */
        @Override
        public String[] getVersion() throws Exception {
            return new String[] {this.getClass().getPackage().getImplementationTitle() + " version: "
                    + this.getClass().getPackage().getImplementationVersion()};
        }
    }
}
