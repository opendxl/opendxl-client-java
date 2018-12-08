package com.opendxl.client.cli;

import org.apache.commons.lang3.StringUtils;
import picocli.CommandLine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

@CommandLine.Command(description = "dxlclient", name = "dxlclient", mixinStandardHelpOptions = true,
        version = "dxlclient <VERSION>", subcommands = {GenerateCsrAndPrivateKeySubcommand.class,
        ProvisionDxlClientSubcommand.class, UpdateConfigSubcommand.class})
public class CommandLineInterface implements Subcommand {

    private CommandLineInterface() {
    }

    public static String getValueFromPrompt(String title, boolean confirm) throws IOException {
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

    public static String readLine(String format, Object... args) throws IOException {
        if (System.console() != null) {
            return System.console().readLine(format, args);
        }
        System.out.print(String.format(format, args));
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                System.in));
        return reader.readLine();
    }

    public static char[] readPassword(String format, Object... args)
            throws IOException {
        if (System.console() != null)
            return System.console().readPassword(format, args);
        return readLine(format, args).toCharArray();
    }

    public static void main(String[] args) {
//        System.setProperty("picocli.trace", "DEBUG");

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

    @Override
    public void execute(CommandLine.ParseResult parseResult) {
        System.out.println("In main cli");
    }
}
