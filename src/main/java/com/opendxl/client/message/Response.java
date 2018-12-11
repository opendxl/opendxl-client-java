/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client.message;

import com.opendxl.client.DxlClient;
import org.msgpack.packer.Packer;
import org.msgpack.unpacker.BufferUnpacker;

import java.io.IOException;
import java.util.Collections;

/**
 * A response for a corresponding {@link Request} message.
 */
public class Response extends Message {
    /**
     * The identifier for the request message that this is a response for.
     */
    private String requestMessageId;

    /**
     * The request (only available when sending the response)
     */
    private Request request;

    /**
     * The GUID of the service that processed the request
     */
    private String serviceGuid;

    /**
     * Constructs the response.
     */
    Response() {
    }

    /**
     * Constructs the response
     *
     * @param client  The client that will be sending this response
     * @param request The request message that this will be a response for
     */
    public Response(final DxlClient client, final Request request) {
        this(client.getUniqueId(), request);
    }

    /**
     * Constructs the response
     *
     * @param request The request message that this will be a response for
     */
    public Response(final Request request) {
        this((String) null, request);
    }

    /**
     * Constructs the response
     *
     * @param sourceClientId The ID of the client that will be sending this message.
     * @param request        The request message that this will be a response for
     */
    public Response(final String sourceClientId, final Request request) {
        this(sourceClientId, request.getReplyToTopic(), request.getMessageId(),
            request.getServiceGuid(), request.getSourceClientInstanceId(), request.getSourceBrokerGuid());

        this.request = request;
    }

    /**
     * Constructs the response
     *
     * @param sourceClientId   The ID of the client that will be sending this message.
     * @param replyToChannel   The channel to send the response to
     * @param requestMessageId The identifier of the request message
     * @param serviceId        The service that handled the request
     * @param destClientId     The identifier of the client to respond to
     * @param destBrokerId     The identifier of the broker that the client being responded to
     *                         is connected to.
     */
    protected Response(
        final String sourceClientId, final String replyToChannel, final String requestMessageId,
        final String serviceId, final String destClientId, final String destBrokerId) {
        super(sourceClientId, replyToChannel);

        this.requestMessageId = requestMessageId;
        this.serviceGuid = serviceId;
        setClientGuids(Collections.singleton(destClientId));
        setBrokerGuids(Collections.singleton(destBrokerId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte getMessageType() {
        return MESSAGE_TYPE_RESPONSE;
    }

    /**
     * Returns the identifier for the request message that this is a response for.
     *
     * @return The identifier for the request message that this is a response for.
     */
    public String getRequestMessageId() {
        return this.requestMessageId;
    }

    /**
     * Returns the request (only available when sending the response)
     *
     * @return The request (only available when sending the response)
     */
    public Request getRequest() {
        return this.request;
    }

    /**
     * Returns the GUID of the service that processed the request
     *
     * @return The GUID of the service that processed the request
     */
    public String getServiceGuid() {
        return this.serviceGuid;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void packMessage(final Packer packer) throws IOException {
        super.packMessage(packer);
        packer.write(this.requestMessageId.getBytes(CHARSET_ASCII));
        packer.write(this.serviceGuid.getBytes(CHARSET_ASCII));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void unpackMessage(final BufferUnpacker unpacker) throws IOException {
        super.unpackMessage(unpacker);
        this.requestMessageId = new String(unpacker.readByteArray(), CHARSET_ASCII);
        this.serviceGuid = new String(unpacker.readByteArray(), CHARSET_ASCII);
    }
}
