/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client.message;

import com.opendxl.client.DxlClient;
import com.opendxl.client.callback.ResponseCallback;
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
 * The base class for the different Data Exchange Layer (DXL) message types.
 * <UL>
 * <LI>{@link Event}</LI>
 * <LI>{@link Request}</LI>
 * <LI>{@link Response}</LI>
 * <LI>{@link ErrorResponse}</LI>
 * </UL>
 * <P>
 * Event messages are sent using the {@link DxlClient#sendEvent} method of a client instance. Event messages are sent
 * by one publisher and received by one or more recipients that are currently subscribed to the
 * {@link Message#getDestinationTopic()} associated with the event (otherwise known as one-to-many).
 * </P>
 * <P>
 * Request messages are sent using the {@link DxlClient#syncRequest} and {@link DxlClient#asyncRequest} methods of a
 * client instance. Request messages are used when invoking a method on a remote service. This communication is
 * one-to-one where a client sends a request to a service instance and in turn receives a response.
 * </P>
 * <P>
 * Response messages are sent by service instances upon receiving Request messages. Response messages are sent using
 * the {@link DxlClient#sendResponse} method of a client instance. Clients that are invoking the service (sending a
 * request) will receive the response as a return value of the {@link DxlClient#syncRequest} method of a client
 * instance or via the {@link ResponseCallback} callback when invoking the asynchronous method,
 * {@link DxlClient#asyncRequest}.
 * </P>
 * <P>
 * {@link ErrorResponse} messages are sent by the DXL fabric itself or service instances upon receiving Request
 * messages. The error response may indicate the inability to locate a service to handle the request or an internal
 * error within the service itself. Error response messages are sent using the {@link DxlClient#sendResponse} method
 * of a client instance.
 * </P>
 * <P>
 * <B>NOTE</B>: Some services may chose to not send a Response message when receiving a Request. This typically occurs
 * if the service is being used to simply collect information from remote clients. In this scenario, the client should
 * use the asynchronous form for sending requests, {@link DxlClient#asyncRequest}.
 * </P>
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
     * The numeric type identifier for the {@link Request} message type
     */
    public static final byte MESSAGE_TYPE_REQUEST = 0;

    /**
     * The numeric type identifier for the {@link Response} message type
     */
    public static final byte MESSAGE_TYPE_RESPONSE = 1;

    /**
     * The numeric type identifier for the {@link Event} message type
     */
    public static final byte MESSAGE_TYPE_EVENT = 2;

    /**
     * The numeric type identifier for the {@link ErrorResponse} message type
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
     * The instance identifier for the client that is the source of the message
     */
    private String sourceClientInstanceId = "";

    /**
     * The identifier of the broker that is the source of the message
     */
    private String sourceBrokerGuid;

    /**
     * The topic that the message is published on
     */
    private String destinationTopic;

    /**
     * The payload to send with the message
     */
    private byte[] payload = emptyPayload;

    /**
     * The set of broker identifiers to deliver the message to
     */
    @SuppressWarnings("unchecked")
    private Set<String> brokerIds = Collections.EMPTY_SET;

    /**
     * The set of client identifiers to deliver the message to
     */
    @SuppressWarnings("unchecked")
    private Set<String> clientIds = Collections.EMPTY_SET;

    /**
     * The tenant identifier of the DXL client that sent the message (set by the broker that initially
     * receives the message)
     */
    @SuppressWarnings("unchecked")
    private String sourceTenantGuid = "";

    /**
     * The set of tenant identifiers that the message is to be routed to.
     */
    @SuppressWarnings("unchecked")
    private Set<String> destTenantGuids = Collections.EMPTY_SET;

    /**
     * The other fields
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> otherFields = Collections.EMPTY_MAP;

    /**
     * Private constructor
     */
    Message() { }

    /**
     * Constructor for {@link Message}
     *
     * @param client The client that will be sending this message
     * @param destinationTopic The topic to publish the message to
     */
    public Message(final DxlClient client, final String destinationTopic) {
        this(client.getUniqueId(), destinationTopic);
    }

    /**
     * Constructor for {@link Message}
     *
     * @param destinationTopic The topic to publish the message to
     */
    public Message(final String destinationTopic) {
        this((String) null, destinationTopic);
    }

    /**
     * Constructor for {@link Message}
     *
     * @param sourceClientId  The identifier of the client that will be sending this message
     * @param destinationTopic The topic to publish the message to
     */
    public Message(final String sourceClientId, final String destinationTopic) {
        if (destinationTopic == null) {
            throw new IllegalArgumentException("Destination must not be null");
        }

        this.messageId = UuidGenerator.generateIdAsString();
        // Set by the client before sending if empty
        // Will be overwritten by broker
        this.sourceClientId = sourceClientId;
        this.destinationTopic = destinationTopic;
        // Set by broker
        this.sourceBrokerGuid = "";
    }

    /**
     * Sets the version of the DXL message (used to determine the features that are available)
     *
     * @param version The version of the DXL message (used to determine the features that are available)
     */
    private void setVersion(final long version) {
        this.version = version;
    }

    /**
     * Returns the version of the DXL message (used to determine the features that are available)
     *
     * @return The version of the DXL message (used to determine the features that are available)
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
     * Returns the unique identifier for the message (UUID)
     *
     * @return The unique identifier for the message (UUID)
     */
    public String getMessageId() {
        return this.messageId;
    }

    /**
     * Returns the identifier of the DXL client that sent the message (set by the broker that initially receives
     * the message)
     *
     * @return The identifier of the DXL client that sent the message (set by the broker that initially
     * receives the message)
     */
    public String getSourceClientId() {
        return this.sourceClientId;
    }

    /**
     * Sets the identifier of the DXL client that sent the message (set by the broker that initially receives
     * the message)
     *
     * @param sourceClientId The identifier of the DXL client that sent the message (set by the broker that initially
     *                       receives the message)
     */
    public void setSourceClientId(String sourceClientId) {
        this.sourceClientId = sourceClientId;
    }

    /**
     * Sets the instance identifier for the client that is the source of the message
     *
     * @param sourceClientInstanceId The instance identifier for the client that is the source of the message
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
     * Returns the identifier of the DXL broker that the message's originating client is connected to (set by the
     * initial broker)
     *
     * @return The identifier of the DXL broker that the message's originating client is connected to (set by the
     *      initial broker)
     */
    public String getSourceBrokerId() {
        return this.sourceBrokerGuid;
    }

    /**
     * Returns the topic to publish the message to
     *
     * @return The topic to publish the message to
     */
    public String getDestinationTopic() {
        return this.destinationTopic;
    }

    /**
     * Sets the topic to publish the message to
     *
     * @param topic The topic to publish the message to
     */
    public void setDestinationTopic(final String topic) {
        this.destinationTopic = topic;
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
     * Returns the application-specific payload of the message (bytes)
     *
     * @return The application-specific payload of the message (bytes)
     */
    public byte[] getPayload() {
        return this.payload;
    }

    /**
     * Sets the broker identifiers that the message is to be routed to. Setting this value will limit which brokers
     * the message will be delivered to. This can be used in conjunction with {@link #setClientIds}.
     *
     * @param brokerIds The broker identifiers that the message is to be routed to
     */
    public void setBrokerIds(final Set<String> brokerIds) {
        this.brokerIds = brokerIds;
    }

    /**
     * Returns the broker identifiers that the message is to be routed to.
     *
     * @return The broker identifiers that the message is to be routed to
     */
    public Set<String> getBrokerIds() {
        return this.brokerIds;
    }

    /**
     * Sets the client identifiers that the message is to be routed to. Setting this value will limit which clients
     * the message will be delivered to. This can be used in conjunction with {@link #setBrokerIds}.
     *
     * @param clientIds The client GUIDs to send the message to
     */
    public void setClientIds(final Set<String> clientIds) {
        this.clientIds = clientIds;
    }

    /**
     * Returns the client identifiers that the message is to be routed to
     *
     * @return The client identifiers that the message is to be routed to
     */
    public Set<String> getClientIds() {
        return this.clientIds;
    }

    /**
     * Sets a {@link Map} containing the set of additional fields associated with the message. These fields can be
     * used to add "header" like values to the message without requiring modifications to be made to the payload.
     *
     * @param otherFields A {@link Map} containing the set of additional fields associated with the message.
     */
    public void setOtherFields(final Map<String, String> otherFields) {
        this.otherFields = otherFields;
    }

    /**
     * Returns a {@link Map} containing the set of additional fields associated with the message. These fields can be
     * used to add "header" like values to the message without requiring modifications to be made to the payload.
     *
     * @return A {@link Map} containing the set of additional fields associated with the message.
     */
    public Map<String, String> getOtherFields() {
        return this.otherFields;
    }

    /**
     * Returns the tenant identifier of the DXL client that sent the message (set by the broker that initially
     * receives the message)
     *
     * @return The tenant identifier of the DXL client that sent the message (set by the broker that initially
     *      receives the message)
     */
    public String getSourceTenantGuid() {
        return this.sourceTenantGuid;
    }

    /**
     * Sets the tenant identifier of the DXL client that sent the message (set by the broker that initially receives
     * the message)
     *
     * @param sourceTenantGuid The tenant identifier of the DXL client that sent the message (set by the broker that
     *                         initially receives the message)
     */
    public void setSourceTenantGuid(final String sourceTenantGuid) {
        this.sourceTenantGuid = sourceTenantGuid;
    }

    /**
     * Returns the set of tenant identifiers that the message is to be routed to.
     *
     * @return The set of tenant identifiers that the message is to be routed to.
     */
    public Set<String> getDestTenantGuids() {
        return this.destTenantGuids;
    }

    /**
     * Sets the tenant identifiers that the message is to be routed to. Setting this value will limit which
     * clients the message will be delivered to. This can be used in conjunction with {@link #setBrokerIds} and
     * {@link #setClientIds}.

     *
     * @param destTenantGuids The set of tenant identifiers that the message is to be routed to.
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
    void packMessage(final Packer packer) throws IOException {
        packer.write(this.messageId.getBytes(CHARSET_ASCII));
        packer.write(this.sourceClientId.getBytes(CHARSET_ASCII));
        packer.write(this.sourceBrokerGuid.getBytes(CHARSET_ASCII));
        packStringSet(packer, getBrokerIds());
        packStringSet(packer, getClientIds());
        packer.write(this.payload);
    }

    /**
     * Unpacks the contents of the message
     *
     * @param unpacker The unpacker
     * @throws IOException If an IO exception occurs
     */
    void unpackMessage(final BufferUnpacker unpacker) throws IOException {
        this.messageId = new String(unpacker.readByteArray(), CHARSET_ASCII);
        this.sourceClientId = new String(unpacker.readByteArray(), CHARSET_ASCII);
        this.sourceBrokerGuid = new String(unpacker.readByteArray(), CHARSET_ASCII);
        this.brokerIds = unpackStringSet(unpacker);
        this.clientIds = unpackStringSet(unpacker);
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
     * Converts the specified array of bytes to a concrete message instance (request, response, error, etc.)
     * and returns it
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
