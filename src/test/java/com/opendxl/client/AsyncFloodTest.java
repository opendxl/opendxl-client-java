/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client;

import com.opendxl.client.message.ErrorResponse;
import com.opendxl.client.message.Request;
import com.opendxl.client.message.Response;
import com.opendxl.client.testutil.impl.DxlClientImplFactory;
import com.opendxl.client.util.UuidGenerator;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Slams a service with a flood of asynchronous tests. The PAHO library by default will
 * deadlock when it is waiting to complete a publish and at the same time receives an
 * incoming message.
 * <p/>
 * This test ensures that the changes we made to the PAHO library now work in this particular
 * scenario.
 */
public class AsyncFloodTest extends AbstractDxlTest {
    /**
     * The count of requests to send
     */
    private static final int REQUEST_COUNT = 1000;

    /**
     * Amount of time to wait for the test to succeed
     */
    private static final long WAIT_TIME = 90 * 1000;

    /**
     * The service registration information
     */
    @SuppressWarnings("FieldCanBeLocal")
    private ServiceRegistrationInfo info;

    /**
     * Flood it.
     *
     * @throws Exception If an error occurs
     */
    @Test
    public void testFlood() throws Exception {
        final AtomicInteger responseCount = new AtomicInteger(0);
        final AtomicInteger errorCount = new AtomicInteger(0);
        final Object responseNotify = new Object();

        final DxlClientConfig config = DxlClientImplFactory.getDefaultInstance().getConfig();
        config.setIncomingMessageQueueSize(10); // Small queue... to make it happen quicker
        final String channel = UuidGenerator.generateIdAsString();

        try (DxlClient client = new DxlClient(config)) {
            this.info = new ServiceRegistrationInfo(client, channel);

            client.connect();
            client.subscribe(channel);
            this.info.addTopic(channel,
                request -> {
                    try {
                        Thread.sleep(50);

                        final Response res = new Response(client, request);
                        res.setPayload(request.getPayload());

                        client.sendResponse(res);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            );
            client.registerServiceSync(this.info, 10 * 1000);

            try (DxlClient client2 = DxlClientImplFactory.getDefaultInstance().newClientInstance()) {
                client2.connect();

                client2.addResponseCallback(null,
                    response -> {
                        if (response instanceof ErrorResponse) {
                            System.out.println("Received error response: "
                                + ((ErrorResponse) response).getErrorMessage());
                            errorCount.incrementAndGet();
                        } else {
                            if (responseCount.incrementAndGet() % 10 == 0) {
                                System.out.println("Received response: "
                                    + responseCount.get() + ", " + response.getClientGuids().iterator().next());
                            }
                        }

                        synchronized (responseNotify) {
                            responseNotify.notify();
                        }
                    }
                );

                for (int i = 0; i < REQUEST_COUNT; i++) {
                    if ((i % 100) == 0) {
                        System.out.println("Sent: " + i);
                    }

                    final Request req = new Request(client2, channel);
                    final String pl = Integer.toString(i);
                    req.setPayload(pl.getBytes());
                    client2.asyncRequest(req);

                    if (errorCount.get() > 0) {
                        break;
                    }
                }

                // Wait for all responses, an error to occur, or we timeout
                final long startTime = System.currentTimeMillis();
                while (
                    (responseCount.get() != REQUEST_COUNT)
                        && (errorCount.get() == 0)
                        && ((System.currentTimeMillis() - startTime) < WAIT_TIME)) {
                    synchronized (responseNotify) {
                        responseNotify.wait(5000);
                    }
                }
                if (errorCount.get() != 0) {
                    // Did we recieve any errors?
                    fail("Received an error response!");
                }

                // Did we receive all of our messages?
                assertEquals("Did not receive all messages!", REQUEST_COUNT, responseCount.get());
            }
        }
    }
}
