/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client.exception;

/**
 * Exception that indicates that a wait timeout has been exceeded
 */
public class WaitTimeoutException extends DxlException {
    /**
     * Constructs the exception
     *
     * @param message The message associated with the exception
     */
    public WaitTimeoutException(final String message) {
        super(message);
    }
}
