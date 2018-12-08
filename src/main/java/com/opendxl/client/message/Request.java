/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client.message;

import com.opendxl.client.DxlClient;
import org.msgpack.packer.Packer;
import org.msgpack.unpacker.BufferUnpacker;

import java.io.IOException;

/**
 * A request message.
 */
public class Request extends Message {
    /**
     * The channel used to reply to this request
     */
    private String replyToChannel;
    /**
     * The service GUID
     */
    private String serviceGuid;

    /**
     * Constructs the request
     */
    Request() {
    }

    /**
     * Constructs the request
     *
     * @param client             The client that will be sending this request.
     * @param destinationChannel The channel to publish the request on.
     */
    public Request(final DxlClient client, final String destinationChannel) {
        this(client.getUniqueId(), destinationChannel);
    }

    /**
     * Constructs the request
     *
     * @param destinationChannel The channel to publish the request on.
     */
    public Request(final String destinationChannel) {
        this((String) null, destinationChannel);
    }

    /**
     * Constructs the request
     *
     * @param sourceClientId     The ID of the client that will be sending this message.
     * @param destinationChannel The channel to publish the request on.
     */
    public Request(final String sourceClientId, final String destinationChannel) {
        super(sourceClientId, destinationChannel);
        this.serviceGuid = "";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte getMessageType() {
        return MESSAGE_TYPE_REQUEST;
    }

    /**
     * Returns the channel used to reply to this request.
     *
     * @return The channel used to reply to this request.
     */
    public String getReplyToChannel() {
        return this.replyToChannel;
    }

    /**
     * Sets the channel used to reply to this request.
     *
     * @param channel The channel used to reply to this request.
     */
    public void setReplyToChannel(final String channel) {
        this.replyToChannel = channel;
    }

    /**
     * Returns the GUID of the service associated with this request
     *
     * @return The GUID of the service associated with this request
     */
    public String getServiceGuid() {
        return this.serviceGuid;
    }

    /**
     * Sets the GUID of the service associated with this request
     *
     * @param serviceGuid The GUID of the service associated with this request
     */
    public void setServiceGuid(final String serviceGuid) {
        this.serviceGuid = serviceGuid;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void packMessage(final Packer packer) throws IOException {
        super.packMessage(packer);
        packer.write(this.replyToChannel.getBytes(CHARSET_ASCII));
        packer.write(this.serviceGuid.getBytes(CHARSET_ASCII));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void unpackMessage(final BufferUnpacker unpacker) throws IOException {
        super.unpackMessage(unpacker);
        this.replyToChannel = new String(unpacker.readByteArray(), CHARSET_ASCII);
        this.serviceGuid = new String(unpacker.readByteArray(), CHARSET_ASCII);
    }
}
