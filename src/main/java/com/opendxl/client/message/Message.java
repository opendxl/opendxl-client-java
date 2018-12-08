/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client.message;

import com.opendxl.client.DxlClient;
import com.opendxl.client.util.UuidGenerator;
import org.msgpack.MessagePack;
import org.msgpack.packer.Packer;
import org.msgpack.unpacker.BufferUnpacker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Abstract base class for the concrete message types (request, response,
 * event, etc.)
 *
 * @see Request
 * @see Response
 * @see Event
 */
public abstract class Message {
    /**
     * ASCII character encoding
     */
    static final String CHARSET_ASCII = "US-ASCII";
    /**
     * UTF-8 character encoding
     */
    public static final String CHARSET_UTF8 = "UTF-8";

    /**
     * The message version
     * <p/>
     * Version history
     * <p/>
     * <code>
     * Version 0:
     * 1.0 to version 1.1:
     * Standard fields.
     * Version 1:
     * 2.0+:
     * Added "other" fields (string array)
     * Version 2:
     * 3.1+:
     * Added "sourceTenantGuid" field (string)
     * Added "tenantGuids" fields (string array)
     * Version 3:
     * 5.0+:
     * Added "sourceClientInstanceId" to support multiple
     * connections per client (string).
     * </code>
     */
    private static final long MESSAGE_VERSION = 3;

    /**
     * The numeric identifier for the request message type
     */
    public static final byte MESSAGE_TYPE_REQUEST = 0;
    /**
     * The numeric identifier for the response message type
     */
    public static final byte MESSAGE_TYPE_RESPONSE = 1;
    /**
     * The numeric identifier for the event message type
     */
    public static final byte MESSAGE_TYPE_EVENT = 2;
    /**
     * The numeric identifier for the error message type
     */
    public static final byte MESSAGE_TYPE_ERROR = 3;

    /**
     * An empty payload
     */
    private static byte[] emptyPayload = new byte[0];

    /**
     * Message pack instance to use for packing/unpacking messages
     */
    private static final MessagePack sm_msgpack = new MessagePack();

    /**
     * The version of the message
     */
    private long version;
    /**
     * The unique identifier for the message
     */
    private String messageId;
    /**
     * The identifier for the client that is the source of the message
     */
    private String sourceClientId;
    /**
     * The instnace identifier for the client that is the source of the message
     */
    private String sourceClientInstanceId = "";
    /**
     * The GUID for the broker that is the source of the message
     */
    private String sourceBrokerGuid;
    /**
     * The channel that the message is published on
     */
    private String destinationChannel;
    /**
     * The payload to send with the message
     */
    private byte[] payload = emptyPayload;
    /**
     * The set of broker GUIDs to deliver the message to
     */
    @SuppressWarnings("unchecked")
    private Set<String> brokerGuids = Collections.EMPTY_SET;
    /**
     * The set of client GUIDs to deliver the message to
     */
    @SuppressWarnings("unchecked")
    private Set<String> clientGuids = Collections.EMPTY_SET;
    /**
     * The set of tenant GUIDs to tenant
     */
    @SuppressWarnings("unchecked")
    private String sourceTenantGuid = "";
    /**
     * The set of tenant GUIDs to deliver the message to
     */
    @SuppressWarnings("unchecked")
    private Set<String> destTenantGuids = Collections.EMPTY_SET;

    /**
     * The other fields
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> otherFields = Collections.EMPTY_MAP;

    /**
     * Constructs the message
     */
    Message() {
    }

    /**
     * Constructs the message
     *
     * @param client             The client that will be sending this message.
     * @param destinationChannel The channel to publish the message on.
     */
    public Message(final DxlClient client, final String destinationChannel) {
        this(client.getUniqueId(), destinationChannel);
    }

    /**
     * Constructs the message
     *
     * @param destinationChannel The channel to publish the message on.
     */
    public Message(final String destinationChannel) {
        this((String) null, destinationChannel);
    }

    /**
     * Constructs the message
     *
     * @param sourceClientId     The ID of the client that will be sending this message.
     * @param destinationChannel The channel to publish the message on.
     */
    public Message(final String sourceClientId, final String destinationChannel) {
        if (destinationChannel == null) {
            throw new IllegalArgumentException("Destination must not be null");
        }

        this.messageId = UuidGenerator.generateIdAsString();
        // Set by the client before sending if empty
        // Will be overwritten by broker
        this.sourceClientId = sourceClientId;
        this.destinationChannel = destinationChannel;
        // Set by broker
        this.sourceBrokerGuid = "";
    }

    /**
     * Sets the message version
     *
     * @param version The message version
     */
    private void setVersion(final long version) {
        this.version = version;
    }

    /**
     * Returns the message version
     *
     * @return The message version
     */
    public long getVersion() {
        return this.version;
    }

    /**
     * Returns the numeric type of the message
     *
     * @return The numeric type of the message
     */
    public abstract byte getMessageType();

    /**
     * Returns the unique identifier for the message
     *
     * @return The unique identifier for the message
     */
    public String getMessageId() {
        return this.messageId;
    }

    /**
     * Returns the identifier for the client that is the source of the message
     *
     * @return The identifier for the client that is the source of the message
     */
    public String getSourceClientId() {
        return this.sourceClientId;
    }

    /**
     * Sets the identifier for the client that is the source of the message
     *
     * @param sourceClientId The identifier for the client that is the source of the message
     */
    public void setSourceClientId(String sourceClientId) {
        this.sourceClientId = sourceClientId;
    }

    /**
     * Sets the identifier for the client that is the source of the message
     *
     * @param sourceClientInstanceId The identifier for the client that is the source of the message
     */
    public void setSourceClientInstanceId(final String sourceClientInstanceId) {
        this.sourceClientInstanceId = sourceClientInstanceId;
    }

    /**
     * Returns the instance identifier for the client that is the source of the message
     *
     * @return The instance identifier for the client that is the source of the message
     */
    public String getSourceClientInstanceId() {
        return ((this.sourceClientInstanceId != null && this.sourceClientInstanceId.length() > 0)
            ? this.sourceClientInstanceId : getSourceClientId());
    }

    /**
     * Returns the GUID for the broker that is the source of the message
     *
     * @return The GUID for the broker that is the source of the message
     */
    public String getSourceBrokerGuid() {
        return this.sourceBrokerGuid;
    }

    /**
     * Returns the channel that the message is published on
     *
     * @return The channel that the message is published on
     */
    public String getDestinationChannel() {
        return this.destinationChannel;
    }

    /**
     * Sets the channel that the message is published on
     *
     * @param channel The channel that the message is published on
     */
    public void setDestinationChannel(final String channel) {
        this.destinationChannel = channel;
    }

    /**
     * The payload for the message
     *
     * @param payload The payload for the message
     */
    public void setPayload(final byte[] payload) {
        this.payload = payload;
    }

    /**
     * Returns the payload for the message
     *
     * @return The payload for the message
     */
    public byte[] getPayload() {
        return this.payload;
    }

    /**
     * Sets the broker GUIDs to send the message to
     *
     * @param brokerGuids The broker GUIDs to send the message to
     */
    public void setBrokerGuids(final Set<String> brokerGuids) {
        this.brokerGuids = brokerGuids;
    }

    /**
     * Returns the set of broker GUIDs to send the message to
     *
     * @return The set of broker GUIDs to send the message to
     */
    public Set<String> getBrokerGuids() {
        return this.brokerGuids;
    }

    /**
     * Sets the client GUIDs to send the message to
     *
     * @param clientGuids The client GUIDs to send the message to
     */
    public void setClientGuids(final Set<String> clientGuids) {
        this.clientGuids = clientGuids;
    }

    /**
     * Returns the set of client GUIDs to send the message to
     *
     * @return The set of client GUIDs to send the message to
     */
    public Set<String> getClientGuids() {
        return this.clientGuids;
    }

    /**
     * Sets the other fields (name-value pairs)
     *
     * @param otherFields The other fields (name-value pairs)
     */
    public void setOtherFields(final Map<String, String> otherFields) {
        this.otherFields = otherFields;
    }

    /**
     * Returns the other fields (name-value pairs)
     *
     * @return The other fields (name-value pairs)
     */
    public Map<String, String> getOtherFields() {
        return this.otherFields;
    }

    /**
     * Returns the tenant GUID that is the source of the message
     *
     * @return The tenant GUID that is the source of the message
     */
    public String getSourceTenantGuid() {
        return this.sourceTenantGuid;
    }

    /**
     * Sets the tenant GUID that is the source of the message
     *
     * @param sourceTenantGuid The tenant GUID that is the source of the message
     */
    public void setSourceTenantGuid(final String sourceTenantGuid) {
        this.sourceTenantGuid = sourceTenantGuid;
    }

    /**
     * Returns the set of tenant GUIDs to send the message to
     *
     * @return The set of tenant GUIDs to send the message to
     */
    public Set<String> getDestTenantGuids() {
        return this.destTenantGuids;
    }

    /**
     * Sets the tenant GUIDs to send the message to
     *
     * @param destTenantGuids The tenant GUIDs to send the message to
     */
    public void setDestTenantGuids(Set<String> destTenantGuids) {
        this.destTenantGuids = destTenantGuids;
    }

    /**
     * Packs a set of strings
     *
     * @param packer The packer
     * @param strSet The string set to pack
     */
    private void packStringSet(final Packer packer, final Set<String> strSet) throws IOException {
        packer.writeArrayBegin(strSet.size());
        for (final String str : strSet) {
            packer.write(str);
        }
        packer.writeArrayEnd();
    }

    /**
     * Unpacks a set of strings
     *
     * @param unpacker The unpacker
     * @return The string set
     */
    private Set<String> unpackStringSet(final BufferUnpacker unpacker) throws IOException {
        //noinspection unchecked
        Set<String> retSet = Collections.EMPTY_SET;
        final int arraySize = unpacker.readArrayBegin();
        if (arraySize > 0) {
            retSet = new HashSet<>(arraySize);
            for (int i = 0; i < arraySize; i++) {
                retSet.add(new String(unpacker.readByteArray(), CHARSET_ASCII));
            }
        }
        unpacker.readArrayEnd();
        return retSet;
    }

    /**
     * Packs a map of strings
     *
     * @param packer The packer
     * @param strMap The string map to pack
     */
    private void packStringMap(final Packer packer, final Map<String, String> strMap) throws IOException {
        packer.writeArrayBegin(strMap.size() << 1);
        for (Map.Entry<String, String> entry : strMap.entrySet()) {
            packer.write(entry.getKey());
            packer.write(entry.getValue());
        }
        packer.writeArrayEnd();
    }

    /**
     * Unpacks a map of strings
     *
     * @param unpacker The unpacker
     * @return The string map
     */
    private Map<String, String> unpackStringMap(final BufferUnpacker unpacker) throws IOException {
        //noinspection unchecked
        Map<String, String> retMap = Collections.EMPTY_MAP;
        final int arraySize = unpacker.readArrayBegin();
        if (arraySize > 0 && ((arraySize % 2) == 0)) {
            retMap = new HashMap<>(arraySize >> 1);
            for (int i = 0; i < arraySize; i += 2) {
                retMap.put(
                    new String(unpacker.readByteArray(), CHARSET_UTF8),
                    new String(unpacker.readByteArray(), CHARSET_UTF8));
            }
        }
        unpacker.readArrayEnd();
        return retMap;
    }

    /**
     * Packs the contents of the message
     *
     * @param packer The packer
     * @throws IOException If an IO exception occurs
     */
    protected void packMessage(final Packer packer) throws IOException {
        packer.write(this.messageId.getBytes(CHARSET_ASCII));
        packer.write(this.sourceClientId.getBytes(CHARSET_ASCII));
        packer.write(this.sourceBrokerGuid.getBytes(CHARSET_ASCII));
        packStringSet(packer, getBrokerGuids());
        packStringSet(packer, getClientGuids());
        packer.write(this.payload);
    }

    /**
     * Unpacks the contents of the message
     *
     * @param unpacker The unpacker
     * @throws IOException If an IO exception occurs
     */
    protected void unpackMessage(final BufferUnpacker unpacker) throws IOException {
        this.messageId = new String(unpacker.readByteArray(), CHARSET_ASCII);
        this.sourceClientId = new String(unpacker.readByteArray(), CHARSET_ASCII);
        this.sourceBrokerGuid = new String(unpacker.readByteArray(), CHARSET_ASCII);
        this.brokerGuids = unpackStringSet(unpacker);
        this.clientGuids = unpackStringSet(unpacker);
        this.payload = unpacker.readByteArray();
    }

    /**
     * Packs the contents of the message (version 1 of the DXL message)
     *
     * @param packer The packer
     */
    private void packMessageV1(final Packer packer) throws IOException {
        packStringMap(packer, getOtherFields());
    }

    /**
     * Unpacks the contents of the message (version 1 of the DXL message)
     *
     * @param unpacker The unpacker
     */
    private void unpackMessageV1(final BufferUnpacker unpacker) throws IOException {
        this.otherFields = unpackStringMap(unpacker);
    }

    /**
     * Packs the contents of the message (version 2 of the DXL message)
     *
     * @param packer The packer
     */
    private void packMessageV2(final Packer packer) throws IOException {
        packer.write(this.sourceTenantGuid.getBytes(CHARSET_ASCII));
        packStringSet(packer, getDestTenantGuids());
    }

    /**
     * Unpacks the contents of the message (version 2 of the DXL message)
     *
     * @param unpacker The unpacker
     */
    private void unpackMessageV2(final BufferUnpacker unpacker) throws IOException {
        this.sourceTenantGuid = new String(unpacker.readByteArray(), CHARSET_ASCII);
        this.destTenantGuids = unpackStringSet(unpacker);
    }

    /**
     * Packs the contents of the message (version 3 of the DXL message)
     *
     * @param packer The packer
     */
    private void packMessageV3(final Packer packer) throws IOException {
        packer.write(this.sourceClientInstanceId.getBytes(CHARSET_ASCII));
    }

    /**
     * Unpacks the contents of the message (version 3 of the DXL message)
     *
     * @param unpacker The unpacker
     */
    private void unpackMessageV3(final BufferUnpacker unpacker) throws IOException {
        this.sourceClientInstanceId = new String(unpacker.readByteArray(), CHARSET_ASCII);
    }

    /**
     * Converts the message to an array of bytes and returns it
     *
     * @return The message as an array of bytes
     * @throws IOException If an IO exception occurs
     */
    public final byte[] toBytes() throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final Packer packer = sm_msgpack.createPacker(out);

        // Write the message version
        packer.write(MESSAGE_VERSION);

        // Write message type
        packer.write(getMessageType());

        // Version 0
        packMessage(packer);
        // Version 1
        packMessageV1(packer);
        // Version 2
        packMessageV2(packer);
        // Version 3
        packMessageV3(packer);

        return out.toByteArray();
    }

    /**
     * Converts the specified array of bytes to a concrete message instance
     * (request, response, error, etc.) and returns it
     *
     * @param bytes The message as an array of bytes
     * @return The corresponding message
     * @throws IOException If an IO exception occurs
     */
    public static Message fromBytes(final byte[] bytes) throws IOException {
        BufferUnpacker unpacker = sm_msgpack.createBufferUnpacker(bytes);
        unpacker.resetReadByteCount();

        // Read the message version
        final long version = unpacker.readLong();

        // Read the message type
        final byte messageType = unpacker.readByte();
        Message message = null;
        switch (messageType) {
            case MESSAGE_TYPE_REQUEST:
                message = new Request();
                break;
            case MESSAGE_TYPE_RESPONSE:
                message = new Response();
                break;
            case MESSAGE_TYPE_EVENT:
                message = new Event();
                break;
            case MESSAGE_TYPE_ERROR:
                message = new ErrorResponse();
                break;
        }

        if (message == null) {
            throw new IOException("Unknown message type when unpacking message");
        }

        // Set the message version
        message.setVersion(version);

        // Unpack version 0
        message.unpackMessage(unpacker);
        // Unpack version 1
        if (version > 0) {
            message.unpackMessageV1(unpacker);
        }
        // Unpack version 2
        if (version > 1) {
            message.unpackMessageV2(unpacker);
        }
        // Unpack version 3
        if (version > 2) {
            message.unpackMessageV3(unpacker);
        }

        return message;
    }

    /**
     * Returns the message pack instance to use for packing/unpacking messages
     *
     * @return The message pack instance to use for packing/unpacking messages
     */
    public static MessagePack getMessagePack() {
        return sm_msgpack;
    }
}
