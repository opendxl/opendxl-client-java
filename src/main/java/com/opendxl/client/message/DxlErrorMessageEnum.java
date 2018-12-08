/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client.message;

/**
 * Enumeration of DXL error messages
 */
public enum DxlErrorMessageEnum {
    /**
     * Indicates that a service was not available for a request (equivalent to HTTP 404)
     */
    @SuppressWarnings("SpellCheckingInspection")
    FABRICSERVICEUNAVAILABLE(Masks.FABRICERRORCODEMASK | 0x00000001, "unable to locate service for request");

    /**
     * Masks associated with error codes
     */
    static class Masks {
        static final int FABRICERRORCODEMASK = 0x80000000;
    }

    /**
     * The error code
     */
    private int errorCode;
    /**
     * The error message
     */
    private String errorMessage;

    /**
     * Constructs the enumeration value
     *
     * @param errorCode    The error code
     * @param errorMessage The error message
     */
    DxlErrorMessageEnum(final int errorCode, final String errorMessage) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    /**
     * Returns the error code
     *
     * @return The error code
     */
    public int getErrorCode() {
        return this.errorCode;
    }

    /**
     * Returns the error message
     *
     * @return The error message
     */
    public String getErrorMessage() {
        return this.errorMessage;
    }
}
