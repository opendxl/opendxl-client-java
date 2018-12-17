/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client.message;

import com.opendxl.client.DxlClient;
import org.msgpack.packer.Packer;
import org.msgpack.unpacker.BufferUnpacker;

import java.io.IOException;

/**
 * {@link ErrorResponse} messages are sent by the DXL fabric itself or service instances upon receiving
 * {@link Request} messages. The error response may indicate the inability to locate a service to handle the request
 * or an internal error within the service itself. Error response messages are sent using the
 * {@link DxlClient#sendResponse} method of a client instance.
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
     * Constructor for {@link ErrorResponse}
     */
    ErrorResponse() {
        super();
    }

    /**
     * Constructor for {@link ErrorResponse}
     *
     * @param client The client that will be sending this response
     * @param request The {@link Request} message that this will be a {@link Response} for
     * @param errorCode The numeric error code
     * @param errorMessage The textual error message
     */
    public ErrorResponse(
        final DxlClient client,
        final Request request,
        final int errorCode,
        final String errorMessage) {
        this(client.getUniqueId(), request, errorCode, errorMessage);
    }

    /**
     * Constructor for {@link ErrorResponse}
     *
     * @param request The {@link Request} message that this will be a {@link Response} for
     * @param errorCode The numeric error code
     * @param errorMessage The textual error message
     */
    public ErrorResponse(
        final Request request,
        final int errorCode,
        final String errorMessage) {
        this((String) null, request, errorCode, errorMessage);
    }

    /**
     * Constructor for {@link ErrorResponse}
     *
     * @param sourceClientId The ID of the client that will be sending this message.
     * @param request The {@link Request} message that this will be a {@link Response} for
     * @param errorCode The numeric error code
     * @param errorMessage The textual error message
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
     * Returns the numeric error code for the error response
     *
     * @return The numeric error code for the error response
     */
    public int getErrorCode() {
        return this.errorCode;
    }

    /**
     * Returns the textual error message
     *
     * @return The textual error message
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
    void packMessage(final Packer packer) throws IOException {
        super.packMessage(packer);
        packer.write(this.errorCode);
        packer.write(this.errorMessage.getBytes(CHARSET_UTF8));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void unpackMessage(final BufferUnpacker unpacker) throws IOException {
        super.unpackMessage(unpacker);
        this.errorCode = unpacker.readInt();
        this.errorMessage = new String(unpacker.readByteArray(), CHARSET_UTF8);
    }
}
