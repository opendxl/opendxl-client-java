/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client.util;

// This is a highly modified version of 'java-configparser: a Python-compatible Java INI parser'.
// [https://github.com/ASzc/java-configparser]
// which is licensed under Apache License, Version 2.0 (the "License").
// The original copyright statement is retained below:

//
// Copyright 2014, 2016 Red Hat Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This is a highly modified version of ''java-configparser: a Python-compatible Java INI parser'
 *
 * @see <a href="https://github.com/ASzc/java-configparser">https://github.com/ASzc/java-configparser</a>
 */
public class IniParser {

    /** Pattern for non-whitespace characters */
    private static final Pattern nonWhitespacePattern = Pattern.compile("\\S");
    /** Pattern for identifying a section */
    private static final Pattern sectionPattern = Pattern.compile("\\[(?<header>[^]]+)\\]");
    /** Comment prefixes */
    private Set<String> commentPrefixes = new HashSet<>();
    /** Delimiters (between key and value) */
    private List<String> delimiters = new ArrayList<>();
    /** Pattern for detecting an option (key) */
    private Pattern optionPattern;
    /** The sections to values map of the parsed configuration file */
    private Map<String, Map<String, String>> sections = new LinkedHashMap<>();

    /**
     * Constructs the parser
     */
    public IniParser() {
        // Comment prefixes
        commentPrefixes.add("#");
        commentPrefixes.add(";");
        // Name-value delimiters
        delimiters.add("=");
        delimiters.add(":");

        // Join delimiters with | character
        StringBuilder sb = new StringBuilder();
        String prefix = "";
        for (String delimiter : delimiters) {
            sb.append(prefix);
            prefix = "|";
            sb.append(Pattern.quote(delimiter));
        }
        String delimiterRegEx = sb.toString();

        // Create the option pattern
        sb = new StringBuilder();
        // Option name: any characters
        sb.append("(?<option>.*?)");
        // Zero or more whitespace
        sb.append("\\s*");
        // Delimiter: one option in delimiterRegEx
        sb.append("(?<vi>");
        sb.append(delimiterRegEx);
        sb.append(")");
        // Zero or more whitespace
        sb.append("\\s*");
        // Value: all remaining characters
        sb.append("(?<value>.*)");
        // End of line
        sb.append("$");

        optionPattern = Pattern.compile(sb.toString());
    }

    /**
     * Returns the values for the specified section
     *
     * @param   sectionName The section name
     * @return  The values for the specified section
     * @throws  Exception If an error occurs
     */
    public Map<String, String> getSection(final String sectionName) throws Exception {
        final Map<String, String> section = sections.get(sectionName);
        if (section == null) {
            throw new Exception("Section not found: " + sectionName);
        }
        return section;
    }

    /**
     * Returns the value for the specified key in the specified section
     *
     * @param   sectionName The section name
     * @param   keyName The key name
     * @return  The value
     * @throws  Exception If an error occurs
     */
    public String getValue(final String sectionName, final String keyName) throws Exception {
        final String value = getSection(sectionName).get(keyName.toLowerCase());
        if (value == null) {
            throw new Exception("Key not found in section '" + sectionName + "': " + keyName);
        }

        return value;
    }

    /**
     * Returns the value for the specified key in the specified section
     *
     * @param   sectionName The section name
     * @param   keyName The key name
     * @param   defaultValue A default value (if a value is not found)
     * @return  The value
     */
    public String getValue(final String sectionName, final String keyName,
                           final String defaultValue) {
        try {
            return getValue(sectionName, keyName);
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    /**
     * Reads teh configuration file from the specified {@link BufferedReader}
     *
     * @param   reader The {@link BufferedReader}
     * @throws  Exception If an error occurs
     */
    public void read(final BufferedReader reader) throws Exception {
        final Map<String, Map<String, List<String>>> unjoinedSections = new LinkedHashMap<>();
        Map<String, List<String>> currSection = null;
        String currSectionName = null;
        String currOptionName = null;
        int indentLevel = 0;
        String line;
        int lineNo = 0;

        while ((line = reader.readLine()) != null) {
            lineNo++;
            boolean comment = false;
            String value = line.trim();

            for (String prefix : commentPrefixes) {
                if (value.startsWith(prefix)) {
                    value = "";
                    comment = true;
                    break;
                }
            }

            if (value.isEmpty()) {
                // For ongoing option values, add an empty line, but only if there was no comment on this line
                if (!comment && currSection != null && currOptionName != null
                    && currSection.containsKey(currOptionName)) {
                    currSection.get(currOptionName).add("");
                }
            } else {
                // Find index of first non-whitespace character in the raw line (not value)
                Matcher nonWhitespaceMatcher = nonWhitespacePattern.matcher(line);
                int firstNonWhitespace = -1;
                if (nonWhitespaceMatcher.find()) {
                    firstNonWhitespace = nonWhitespaceMatcher.start();
                }
                // This is the indent level, otherwise it is zero
                int currIndentLevel = Math.max(firstNonWhitespace, 0);

                // Continuation line
                if (currSection != null && currOptionName != null && currIndentLevel > indentLevel) {
                    currSection.get(currOptionName).add(value);
                // Section/option header
                } else {
                    indentLevel = currIndentLevel;

                    Matcher sectionMatcher = sectionPattern.matcher(value);
                    // Section header
                    if (sectionMatcher.matches()) {
                        currSectionName = sectionMatcher.group("header");
                        if (unjoinedSections.containsKey(currSectionName)) {
                            throw new Exception("Duplicate section '" + currSectionName + "' at line: " + lineNo);
                        } else {
                            currSection = new LinkedHashMap<>();
                            unjoinedSections.put(currSectionName, currSection);
                        }
                        // So sections can't start with a continuation line
                        currOptionName = null;
                    // No section header in file
                    } else if (currSection == null) {
                        throw new Exception("Missing section header at line: " + lineNo + " (" + line + ")");
                    // Option header
                    } else {
                        Matcher optionMatcher = optionPattern.matcher(value);
                        if (optionMatcher.matches()) {
                            currOptionName = optionMatcher.group("option");
                            String optionValue = optionMatcher.group("value");
                            if (currOptionName == null || currOptionName.length() == 0) {
                                throw new Exception("Invalid line: " + lineNo + " (" + line + ")");
                            }
                            currOptionName = currOptionName.toLowerCase().trim();
                            if (unjoinedSections.get(currSectionName).containsKey(currOptionName)) {
                                throw new Exception("Duplicate key '" + currOptionName + "' in section '"
                                    + currSectionName + "' at line: " + lineNo);
                            } else {
                                final LinkedList<String> valueList = new LinkedList<>();
                                if (optionValue != null) {
                                    valueList.add(optionValue.trim());
                                }
                                currSection.put(currOptionName, valueList);
                            }
                        } else {
                            throw new Exception("Invalid line at: " + lineNo + "(" + line + ")");
                        }
                    }
                }
            }
        }

        // Join multi line values
        for (Entry<String, Map<String, List<String>>> unjoinedSectionEntry : unjoinedSections.entrySet()) {
            final String unjoinedSectionName = unjoinedSectionEntry.getKey();
            final Map<String, List<String>> unjoinedSectionOptions = unjoinedSectionEntry.getValue();
            final Map<String, String> sectionOptions = new LinkedHashMap<>();

            for (Entry<String, List<String>> unjoinedOptionValueEntry : unjoinedSectionOptions.entrySet()) {
                String unjoinedOptionName = unjoinedOptionValueEntry.getKey();
                List<String> unjoinedOptionValue = unjoinedOptionValueEntry.getValue();

                String optionValue;
                if (unjoinedOptionValue.size() > 0) {
                    // Remove trailing whitespace lines
                    ListIterator<String> iter = unjoinedOptionValue.listIterator(unjoinedOptionValue.size());
                    while (iter.hasPrevious()) {
                        if (iter.previous().trim().isEmpty()) {
                            iter.remove();
                        } else {
                            break;
                        }
                    }

                    // Join lines with newline character
                    StringBuilder optionValueBuilder = new StringBuilder();
                    String prefix = "";
                    for (String valueLine : unjoinedOptionValue) {
                        optionValueBuilder.append(prefix);
                        prefix = "\n";
                        optionValueBuilder.append(valueLine);
                    }
                    optionValue = optionValueBuilder.toString();
                } else {
                    optionValue = null;
                }

                sectionOptions.put(unjoinedOptionName, optionValue);
            }

            sections.put(unjoinedSectionName, sectionOptions);
        }
    }

    /**
     * Reads the specified configuration file
     *
     * @param   iniPath The path to the configuration file
     * @throws  Exception If an error occurs
     */
    public void read(final String iniPath) throws Exception {
        if (iniPath == null) {
            throw new IllegalArgumentException("The specified path is null");
        }
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(iniPath), StandardCharsets.UTF_8)) {
            read(reader);
        }
    }

    /**
     * Writes the current configuration to the specified file
     *
     * @param   iniPath The path to the destination configuration file
     * @throws  Exception If an error occurs
     */
    public void write(final String iniPath) throws Exception {
        if (iniPath == null) {
            throw new IllegalArgumentException("The specified path is null");
        }
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(iniPath), StandardCharsets.UTF_8)) {
            // Create option/value delimiter string using first in configured delimiters
            final String delimiter = " " + delimiters.get(0) + " ";

            // Write out each section
            for (Entry<String, Map<String, String>> sectionEntry : sections.entrySet()) {
                String sectionName = sectionEntry.getKey();
                Map<String, String> sectionOptions = sectionEntry.getValue();

                // Section Header (ex: [mysection])
                writer.append("[");
                writer.append(sectionName);
                writer.append("]");
                writer.newLine();

                // Write out each option/value pair
                for (Entry<String, String> optionEntry : sectionOptions.entrySet()) {
                    String option = optionEntry.getKey();
                    String value = optionEntry.getValue();

                    // Option Header (ex: key = value)
                    writer.append(option);
                    writer.append(delimiter);
                    writer.append(value.replace("\n", System.lineSeparator() + "\t"));
                    writer.newLine();
                }

                writer.newLine();
            }
        }
    }
}