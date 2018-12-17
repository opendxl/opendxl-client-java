/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client.message;

import com.opendxl.client.DxlClient;
import org.msgpack.packer.Packer;
import org.msgpack.unpacker.BufferUnpacker;

import java.io.IOException;

/**
 * Event messages are sent using the {@link DxlClient#sendEvent} method of a client instance. Event messages are sent
 * by one publisher and received by one or more recipients that are currently subscribed to the
 * {@link Message#getDestinationTopic} associated with the event (otherwise known as one-to-many).
 */
public class Event extends Message {
    /**
     * Constructor for {@link Event} message
     */
    Event() {
    }

    /**
     * Constructor for {@link Event} message
     *
     * @param client The client that will be sending this event.
     * @param destinationTopic The topic to publish the message to
     */
    public Event(final DxlClient client, final String destinationTopic) {
        this(client.getUniqueId(), destinationTopic);
    }

    /**
     * Constructor for {@link Event} message
     *
     * @param destinationTopic The topic to publish the message to
     */
    public Event(final String destinationTopic) {
        this((String) null, destinationTopic);
    }

    /**
     * Constructor for {@link Event} message
     *
     * @param sourceClientId  The identifier of the client that will be sending this message.
     * @param destinationTopic The topic to publish the message to
     */
    public Event(final String sourceClientId, final String destinationTopic) {
        super(sourceClientId, destinationTopic);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte getMessageType() {
        return MESSAGE_TYPE_EVENT;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void packMessage(Packer packer) throws IOException {
        super.packMessage(packer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void unpackMessage(BufferUnpacker unpacker) throws IOException {
        super.unpackMessage(unpacker);
    }
}
