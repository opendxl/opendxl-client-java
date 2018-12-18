/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client.cli;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import picocli.CommandLine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandles;

/**
 * Main class for the OpenDXL Java Client CLI commands.
 * <p>
 * There are three CLI commands for the OpenDXL Java Client:
 * </p>
 * <ul>
 * <li>provisionconfig - provisioning a DXL client</li>
 * <li>updateconfig - update the DXL client configuration</li>
 * <li>generatecsr - generate a private key and CSR</li>
 * </ul>
 */
@CommandLine.Command(description = "dxlclient", name = "dxlclient", mixinStandardHelpOptions = true,
        version = "dxlclient <VERSION>", subcommands = {GenerateCsrAndPrivateKeySubcommand.class,
        ProvisionDxlClientSubcommand.class, UpdateConfigSubcommand.class},
        versionProvider = CommandLineInterface.ManifestVersionProvider.class)
public class CommandLineInterface extends Subcommand {

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
        //create log4j appender
        ConsoleAppender console = new ConsoleAppender();
        //configure the appender
        console.setLayout(new PatternLayout(LOGGING_PATTERN));
        console.setThreshold(Level.INFO); // TODO allow the user to set the log level via CLI argument??
        console.activateOptions();
        //add appender to any Logger (here is root)
        Logger.getRootLogger().addAppender(console);

        // Set up the parser
        CommandLine commandLine = new CommandLine(new CommandLineInterface());
        CommandLine.Model.CommandSpec commandSpec = commandLine.getCommandSpec();
        commandSpec.parser().collectErrors(true);

        commandLine.getSubcommands().forEach((k, v) -> v.getCommandSpec().parser().collectErrors(true));

        CommandLine parser = new CommandLine(commandSpec);
        CommandLine.ParseResult parseResult = parser.parseArgs(args);

        if (parseResult.hasSubcommand()) {
            parseResult = parseResult.subcommand(); // Set parseResult to the subcommand
        }

        boolean exit = false;

        if (parseResult.isUsageHelpRequested() || !parseResult.errors().isEmpty() || (!parseResult.hasSubcommand()
                && parseResult.matchedOptions().isEmpty()
                && parseResult.matchedPositionals().isEmpty())) {
            parseResult.commandSpec().commandLine().usage(System.out);
            exit = true;
        }

        if (parseResult.isVersionHelpRequested()) {
            parseResult.commandSpec().commandLine().printVersionHelp(System.out);
            exit = true;
        }

        if (!parseResult.errors().isEmpty() && (!parseResult.matchedOptions().isEmpty()
                || !parseResult.matchedPositionals().isEmpty())) {
            parseResult.errors().forEach(error -> System.out.println(error.getMessage()));
            exit = true;
        }

        if (exit) {
            return;
        }

        Subcommand parsedCommand = parseResult.commandSpec().commandLine().getCommand();
        parsedCommand.execute(parseResult);
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
