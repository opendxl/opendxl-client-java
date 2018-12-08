/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client.message;

import com.opendxl.client.DxlClient;
import org.msgpack.packer.Packer;
import org.msgpack.unpacker.BufferUnpacker;

import java.io.IOException;

/**
 * Represents an error that occurred when attempting to generate a response
 * to a request.
 */
public class ErrorResponse extends Response {
    /**
     * The error code
     */
    private int errorCode;

    /**
     * The error message
     */
    private String errorMessage;

    /**
     * Constructs the error response
     */
    ErrorResponse() {
        super();
    }

    /**
     * Constructs the error response
     *
     * @param client       The client that will be sending this response
     * @param request      The request message that this will be a response for
     * @param errorCode    The error code
     * @param errorMessage The error message
     */
    public ErrorResponse(
        final DxlClient client,
        final Request request,
        final int errorCode,
        final String errorMessage) {
        this(client.getUniqueId(), request, errorCode, errorMessage);
    }

    /**
     * Constructs the error response
     *
     * @param request      The request message that this will be a response for
     * @param errorCode    The error code
     * @param errorMessage The error message
     */
    public ErrorResponse(
        final Request request,
        final int errorCode,
        final String errorMessage) {
        this((String) null, request, errorCode, errorMessage);
    }

    /**
     * Constructs the error response
     *
     * @param sourceClientId The ID of the client that will be sending this message.
     * @param request        The request message that this will be a response for
     * @param errorCode      The error code
     * @param errorMessage   The error message
     */
    public ErrorResponse(
        final String sourceClientId,
        final Request request,
        final int errorCode,
        final String errorMessage) {
        super(sourceClientId, request);

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
     * Returns the error code as a hex string
     *
     * @return The error code as a hex string
     */
    public String getErrorCodeAsHex() {
        return String.format(
            "0x%8s", Integer.toHexString(this.errorCode).replace(' ', '0'));
    }

    /**
     * Returns the error message
     *
     * @return The error message
     */
    public String getErrorMessage() {
        return this.errorMessage;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte getMessageType() {
        return MESSAGE_TYPE_ERROR;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void packMessage(final Packer packer) throws IOException {
        super.packMessage(packer);
        packer.write(this.errorCode);
        packer.write(this.errorMessage.getBytes(CHARSET_UTF8));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void unpackMessage(final BufferUnpacker unpacker) throws IOException {
        super.unpackMessage(unpacker);
        this.errorCode = unpacker.readInt();
        this.errorMessage = new String(unpacker.readByteArray(), CHARSET_UTF8);
    }
}
