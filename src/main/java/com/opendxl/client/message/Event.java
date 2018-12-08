/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client.message;

import com.opendxl.client.DxlClient;
import org.msgpack.packer.Packer;
import org.msgpack.unpacker.BufferUnpacker;

import java.io.IOException;

/**
 * An event message
 */
public class Event extends Message {
    /**
     * Constructs the event message
     */
    Event() {
    }

    /**
     * Constructs the event message
     *
     * @param client             The client that will be sending this event.
     * @param destinationChannel The channel to publish the event on.
     */
    public Event(final DxlClient client, final String destinationChannel) {
        this(client.getUniqueId(), destinationChannel);
    }

    /**
     * Constructs the event message
     *
     * @param destinationChannel The channel to publish the event on.
     */
    public Event(final String destinationChannel) {
        this((String) null, destinationChannel);
    }

    /**
     * Constructs the event message
     *
     * @param sourceClientId     The ID of the client that will be sending this message.
     * @param destinationChannel The channel to publish the event on.
     */
    public Event(final String sourceClientId, final String destinationChannel) {
        super(sourceClientId, destinationChannel);
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
    protected void packMessage(Packer packer) throws IOException {
        super.packMessage(packer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void unpackMessage(BufferUnpacker unpacker) throws IOException {
        super.unpackMessage(unpacker);
    }
}
