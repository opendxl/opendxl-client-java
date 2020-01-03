/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client.message;

import com.opendxl.client.DxlClient;
import org.msgpack.packer.Packer;
import org.msgpack.unpacker.BufferUnpacker;

import java.io.IOException;

/**
 * A {@link Request} messages are sent using the {@link DxlClient#syncRequest} and {@link DxlClient#asyncRequest}
 * methods of a client instance. Request messages are used when invoking a method on a remote service.
 * This communication is one-to-one where a client sends a request to a service instance and in turn receives
 * a response.
 */
public class Request extends Message {

    /**
     * The topic used to reply to this request
     */
    private String replyToTopic;

    /**
     * The service identifier
     */
    private String serviceId;

    /**
     * Whether to perform a multi-service request
     */
    private boolean isMultiService = false;

    /**
     * Constructor for {@link Request}
     */
    protected Request() { }

    /**
     * Constructor for {@link Request}
     *
     * @param client The client that will be sending this request.
     * @param destinationTopic The topic to publish the request to
     */
    public Request(final DxlClient client, final String destinationTopic) {
        this(client.getUniqueId(), destinationTopic);
    }

    /**
     * Constructor for {@link Request}
     *
     * @param destinationTopic The topic to publish the request to
     */
    public Request(final String destinationTopic) {
        this((String) null, destinationTopic);
    }

    /**
     * Constructor for {@link Request}
     *
     * @param sourceClientId The identifier of the client that will be sending this message.
     * @param destinationTopic The topic to publish the request to
     */
    public Request(final String sourceClientId, final String destinationTopic) {
        super(sourceClientId, destinationTopic);
        this.serviceId = "";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte getMessageType() {
        return MESSAGE_TYPE_REQUEST;
    }

    /**
     * Returns the topic that the {@link Response} to this {@link Request} will be sent to
     *
     * @return The topic that the {@link Response} to this {@link Request} will be sent to
     */
    public String getReplyToTopic() {
        return this.replyToTopic;
    }

    /**
     * Sets the topic that the {@link Response} to this {@link Request} will be sent to
     *
     * @param topic the topic that the {@link Response} to this {@link Request} will be sent to
     */
    public void setReplyToTopic(final String topic) {
        this.replyToTopic = topic;
    }

    /**
     * Returns the identifier of the service that this request will be routed to. If an identifier is not specified,
     * the initial broker that receives the request will select the service to handle the request
     * (round-robin by default).
     *
     * @return The identifier of the service that this request will be routed to.
     */
    public String getServiceId() {
        return this.serviceId;
    }

    /**
     * Sets the identifier of the service that this request will be routed to.
     *
     * @param serviceId The identifier of the service that this request will be routed to.
     */
    public void setServiceId(final String serviceId) {
        this.serviceId = serviceId;
    }

    /**
     * Returns whether this is a multi-service request
     *
     * @return Whether this is a multi-service request
     */
    public boolean isMultiServiceRequest() {
        return this.isMultiService;
    }

    /**
     * Sets whether this is a multi-service request
     *
     * @param isMultiService Whether this is a multi-service request
     */
    public void setMultiServiceRequest(final boolean isMultiService) {
        this.isMultiService = isMultiService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void packMessage(final Packer packer) throws IOException {
        super.packMessage(packer);
        packer.write(this.replyToTopic.getBytes(CHARSET_ASCII));
        packer.write(this.serviceId.getBytes(CHARSET_ASCII));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void unpackMessage(final BufferUnpacker unpacker) throws IOException {
        super.unpackMessage(unpacker);
        this.replyToTopic = new String(unpacker.readByteArray(), CHARSET_ASCII);
        this.serviceId = new String(unpacker.readByteArray(), CHARSET_ASCII);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void packMessageV4(Packer packer) throws IOException {
        super.packMessageV4(packer);
        packer.write((byte) (this.isMultiService ? 1 : 0));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void unpackMessageV4(BufferUnpacker unpacker) throws IOException {
        super.unpackMessageV4(unpacker);
        this.isMultiService = (unpacker.readByte() == 1);
    }
}
