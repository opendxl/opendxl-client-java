/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client.message;

import com.opendxl.client.DxlClient;
import com.opendxl.client.callback.ResponseCallback;
import org.msgpack.packer.Packer;
import org.msgpack.unpacker.BufferUnpacker;

import java.io.IOException;
import java.util.Collections;

/**
 * {@link Response} messages are sent by service instances upon receiving {@link Request} messages.
 * {@link Response} messages are sent using the {@link DxlClient#sendResponse} method of a client instance. Clients
 * that are invoking the service (sending a request) will receive the response as a return value of the
 * {@link DxlClient#syncRequest} method of a client instance or via the {@link ResponseCallback} callback when
 * invoking the asynchronous method, {@link DxlClient#asyncRequest}.
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
     * The identifier of the service that processed the request
     */
    private String serviceId;

    /**
     * Constructor for {@link Response}
     */
    public Response() {
    }

    /**
     * Constructor for {@link Response}
     *
     * @param client The client that will be sending this response
     * @param request The {@link Request} message that this is a response for
     */
    public Response(final DxlClient client, final Request request) {
        this(client.getUniqueId(), request);
    }

    /**
     * Constructor for {@link Response}
     *
     * @param request The {@link Request} message that this is a response for
     */
    public Response(final Request request) {
        this((String) null, request);
    }

    /**
     * Constructor for {@link Response}
     *
     * @param sourceClientId The identifier of the client that will be sending this message.
     * @param request The {@link Request} message that this is a response for
     */
    public Response(final String sourceClientId, final Request request) {
        this(sourceClientId, request.getReplyToTopic(), request.getMessageId(),
            request.getServiceId(), request.getSourceClientInstanceId(), request.getSourceBrokerId());

        this.request = request;
    }

    /**
     * Constructor for {@link Response}
     *
     * @param sourceClientId  The identifier of the client that will be sending this message.
     * @param replyToTopic  The toipc to send the response to
     * @param requestMessageId The identifier of the request message
     * @param serviceId The service that handled the request
     * @param destClientId The identifier of the client to respond to
     * @param destBrokerId The identifier of the broker that the client being responded to is connected to.
     */
    protected Response(
        final String sourceClientId, final String replyToTopic, final String requestMessageId,
        final String serviceId, final String destClientId, final String destBrokerId) {
        super(sourceClientId, replyToTopic);

        this.requestMessageId = requestMessageId;
        this.serviceId = serviceId;
        setClientIds(Collections.singleton(destClientId));
        setBrokerIds(Collections.singleton(destBrokerId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte getMessageType() {
        return MESSAGE_TYPE_RESPONSE;
    }

    /**
     * Returns the unique identifier (UUID) for the {@link Request} message that this message is a response for.
     * This is used by the invoking {@link DxlClient} to correlate an incoming {@link Response} message with the
     * {@link Request} message that was initially sent by the client.
     *
     * @return The unique identifier (UUID) for the {@link Request} message that this message is a response for.
     */
    public String getRequestMessageId() {
        return this.requestMessageId;
    }

    /**
     * Returns the {@link Request} message that this is a response for
     *
     * @return The {@link Request} message that this is a response for
     */
    public Request getRequest() {
        return this.request;
    }

    /**
     * Returns the identifier of the service that sent this response (the service that the corresponding
     * {@link Request} was routed to).
     *
     * @return The identifier of the service that sent this response (the service that the corresponding
     *      {@link Request} was routed to)
     */
    public String getServiceId() {
        return this.serviceId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void packMessage(final Packer packer) throws IOException {
        super.packMessage(packer);
        packer.write(this.requestMessageId.getBytes(CHARSET_ASCII));
        packer.write(this.serviceId.getBytes(CHARSET_ASCII));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void unpackMessage(final BufferUnpacker unpacker) throws IOException {
        super.unpackMessage(unpacker);
        this.requestMessageId = new String(unpacker.readByteArray(), CHARSET_ASCII);
        this.serviceId = new String(unpacker.readByteArray(), CHARSET_ASCII);
    }
}
