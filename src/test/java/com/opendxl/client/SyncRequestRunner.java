/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client;

import com.opendxl.client.message.Request;
import com.opendxl.client.message.Response;
import com.opendxl.client.testutil.ThreadPerRunExecutor;
import com.opendxl.client.util.UuidGenerator;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * <P>
 * Tests the synchronous request methods of the {@link DxlClient}.
 * </P>
 * This test sends synchronous requests to a test service and ensures that all of the
 * requests are responded to appropriately.
 *
 * @see DxlClient#syncRequest(com.opendxl.client.message.Request, long);
 */
public class SyncRequestRunner extends AbstractRunner {

    /**
     * The number of requests to send
     */
    private static final int REQUEST_COUNT = 1000;

    /**
     * Maximum time to wait for the test to complete
     */
    private static final int MAX_WAIT = 5 * 60 * 1000;

    /**
     * Maximum time to wait for a response
     */
    private static final int RESPONSE_WAIT = 60 * 1000;

    @Override
    public void runTest(final DxlClientFactory clientFactory) throws Exception {
        try (DxlClient client = clientFactory.newClientInstance();
             TestService testService = new TestService(client)) {
            final AtomicInteger responseCount = new AtomicInteger();

            client.connect();

            final String topic = UuidGenerator.generateIdAsString();

            //
            // Create a test service that responds to requests on a particular topic.
            //
            // Register the service
            final ServiceRegistrationInfo regInfo =
                new ServiceRegistrationInfo(
                    client,
                    "syncRequestRunnerService"
                );
            regInfo.addTopic(topic, testService);
            client.registerServiceSync(regInfo, DEFAULT_TIMEOUT);

            //
            // Sends synchronous requests with a unique thread for each request. Ensure that the
            // response that is received corresponds with the request that was sent. Also, keep
            // track of the total number of responses received.
            //
            final ThreadPerRunExecutor executor = new ThreadPerRunExecutor(REQUEST_COUNT);
            executor.execute(
                () -> {
                    try {
                        final Request request = new Request(client, topic);
                        final Response response = client.syncRequest(request, RESPONSE_WAIT);
                        assertIsResponse(response);
                        assertEquals(request.getMessageId(), response.getRequestMessageId());

                        responseCount.incrementAndGet();
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                }
            );

            // Wait for all of the requests to complete
            if (!executor.joinThreads(MAX_WAIT)) {
                fail("Timeout waiting for requests to complete.");
            }

            // Ensure all of the responses were received
            assertEquals(REQUEST_COUNT, responseCount.get());

            // Unregister the service
            client.unregisterServiceSync(regInfo, DEFAULT_TIMEOUT);
        }
    }
}
