/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client.exception;

/**
 * A general Data Exchange Layer (DXL) Exception
 */
public class DxlException extends Exception {

    /**
     * Constructs the exception
     *
     * @param message The message associated with the exception
     */
    public DxlException(final String message) {
        super(message);
    }

    /**
     * Constructs the exception
     *
     * @param message The message associated with the exception
     * @param cause The cause of the exception
     */
    public DxlException(final String message, final Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs the exception
     *
     * @param cause The cause of the exception
     */
    public DxlException(final Throwable cause) {
        super(cause);
    }

    /** {@inheritDoc} */
    @Override
    public String getMessage() {
        final StringBuilder buff = new StringBuilder(super.getMessage());
        Throwable cause = getCause();
        while (cause != null) {
            buff.append(": ");
            buff.append(cause.getMessage());
            cause = cause.getCause();
        }
        return buff.toString();
    }
}
