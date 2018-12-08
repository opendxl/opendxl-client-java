/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client.exception;

/**
 * A malformed broker exception for use with Data Exchange Layer (DXL)
 */
public class MalformedBrokerException extends DxlException {
    /**
     * Constructs a <code>MalformedBrokerException</code> with the
     * specified detail message.
     *
     * @param message The message associated with the exception
     */
    public MalformedBrokerException(final String message) {
        super(message);
    }

    /**
     * Constructs a <code>MalformedBrokerException</code> with the
     * specified detail message.
     *
     * @param message The message associated with the exception
     * @param cause   The cause of the exception
     */
    public MalformedBrokerException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
