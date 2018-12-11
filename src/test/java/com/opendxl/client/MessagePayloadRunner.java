/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client;

import com.opendxl.client.message.Message;
import com.opendxl.client.message.Request;
import com.opendxl.client.util.UuidGenerator;
import org.msgpack.MessagePack;
import org.msgpack.packer.Packer;
import org.msgpack.unpacker.BufferUnpacker;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Tests whether payloads can be successfully delivered from a client to the server.
 * Payloads are simply bytes of data that are used to provide application-specific
 * information.
 *
 * @see com.opendxl.client.message.Message#setPayload(byte[])
 * @see com.opendxl.client.message.Message#getPayload()
 */
public class MessagePayloadRunner extends AbstractRunner {
    /**
     * A test string to send
     */
    private static final String TEST_STRING = "SslUtils";
    /**
     * A test byte to send
     */
    private static final byte TEST_BYTE = 1;
    /**
     * A test integer
     */
    private static final int TEST_INT = 123456;

    /**
     * {@inheritDoc}
     */
    @Override
    public void runTest(final DxlClientFactory clientFactory) throws Exception {
        try (DxlClient server = clientFactory.newClientInstance()) {
            server.connect();

            final Lock lock = new ReentrantLock();
            final Condition requestCompleteCondition = lock.newCondition();
            final AtomicBoolean receivedRequest = new AtomicBoolean(false);

            //
            // Create a server that handles a request, unpacks the payload, and
            // asserts that the information in the payload was delivered successfully.
            //
            final String topic = UuidGenerator.generateIdAsString();

            // Register the service
            final ServiceRegistrationInfo regInfo =
                new ServiceRegistrationInfo(
                    server,
                    "messagePayloadRunnerService"
                );
            regInfo.addTopic(topic,
                request -> {
                    try {
                        final MessagePack pack = Message.getMessagePack();
                        final BufferUnpacker unpacker =
                            pack.createBufferUnpacker(request.getPayload());
                        assertEquals(TEST_STRING, unpacker.readString());
                        assertEquals(TEST_BYTE, unpacker.readByte());
                        assertEquals(TEST_INT, unpacker.readInt());

                        lock.lock();
                        try {
                            receivedRequest.set(true);
                            requestCompleteCondition.signalAll();
                        } finally {
                            lock.unlock();
                        }
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                }
            );

            server.registerServiceSync(regInfo, DEFAULT_TIMEOUT);

            try (DxlClient client = clientFactory.newClientInstance()) {
                client.connect();

                //
                // Send a request to the server with information contained
                // in the payload
                //
                final Request request = new Request(client, topic);
                final MessagePack pack = Message.getMessagePack();
                final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                final Packer packer = pack.createPacker(baos);
                packer.write(TEST_STRING);
                packer.write(TEST_BYTE);
                packer.write(TEST_INT);
                request.setPayload(baos.toByteArray());
                client.asyncRequest(request);

                //
                // Wait until the request has been processed
                //
                lock.lock();
                try {
                    if (!receivedRequest.get()) {
                        // TODO: Fix this... should check variable prior to wait...
                        if (!requestCompleteCondition.await(5000, TimeUnit.MILLISECONDS)) {
                            fail("Request was not processed.");
                        }
                    }
                } finally {
                    lock.unlock();
                }
            }

            server.unregisterServiceSync(regInfo, DEFAULT_TIMEOUT);
        }
    }
}
