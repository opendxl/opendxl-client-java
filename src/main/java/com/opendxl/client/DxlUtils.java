/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client;

import com.opendxl.client.exception.DxlException;

/**
 * Utility methods for use by the DXL-related classes
 */
class DxlUtils {

    /**
     * Private constructor
     */
    private DxlUtils() {
        super();
    }

    /**
     * Wraps the specified exception as a {@link DxlException} with the specified message. If the incoming exception is
     * already a {@link DxlException}, it is simply rethrown (no wrapping occurs).
     *
     * @param message The message for the exception
     * @param ex The exception to wrap
     * @throws DxlException If a DXL exception occurs
     */
    public static void throwWrappedException(
        final String message, final Exception ex) throws DxlException {
        if (ex instanceof DxlException) {
            throw (DxlException) ex;
        }
        throw new DxlException(message, ex);
    }

    /**
     * Iterates the wildcards for the specified topic.
     * <P>
     * <b>NOTE</b>: This only supports "#" wildcards (not "+").
     * </P>
     *
     * @param cb The callback to invoke for each wildcard
     * @param topic The topic
     */
    public static void iterateWildcards(final WildcardCallback cb, final String topic) {
        if (topic == null) {
            return;
        }

        String modifiedTopic = topic;

        while (true) {
            final int length = modifiedTopic.length();
            if (length == 1 && modifiedTopic.charAt(0) == '#') {
                return;
            }

            int poundPos = 0;
            int searchPos = length - 1;

            if (length >= 2
                && modifiedTopic.charAt(searchPos) == '#'
                && modifiedTopic.charAt(searchPos - 1) == '/') {
                searchPos -= 2;
            }

            while (searchPos >= 0) {
                if (modifiedTopic.charAt(searchPos) == '/') {
                    poundPos = searchPos + 1;
                    break;
                }
                searchPos--;
            }

            modifiedTopic = modifiedTopic.substring(0, poundPos) + "#";
            cb.onNextWildcard(modifiedTopic);
        }
    }

    /**
     * Callback that is invoked for each wildcard pattern found
     */
    public interface WildcardCallback {
        /**
         * Invoked for the next wildcard pattern found
         *
         * @param wildcard The wildcard pattern
         */
        void onNextWildcard(String wildcard);
    }
}
