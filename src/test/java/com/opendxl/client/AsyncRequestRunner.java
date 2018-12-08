/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client;

import com.opendxl.client.callback.ResponseCallback;
import com.opendxl.client.message.Request;
import com.opendxl.client.testutil.TestService;
import com.opendxl.client.util.UuidGenerator;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Tests the asynchronous request methods of the {@link DxlClient}.
 * <p/>
 * This test sends asynchronous requests to a test service and ensures that all of the
 * requests are responded to appropriately. The initiating client is informed of the responses
 * via registered {@link ResponseCallback} callbacks (one channel-specific, and one global).
 *
 * @see DxlClient#asyncRequest(com.opendxl.client.message.Request)
 * @see DxlClient#asyncRequest(com.opendxl.client.message.Request, com.opendxl.client.callback.ResponseCallback)
 * @see DxlClient#addRequestCallback(String, com.opendxl.client.callback.RequestCallback)
 * @see DxlClient#addResponseCallback(String, com.opendxl.client.callback.ResponseCallback)
 */
public class AsyncRequestRunner extends AbstractRunner {
    /**
     * The number of requests to send
     */
    private static final int REQ_COUNT = 10000;

    /**
     * {@inheritDoc}
     */
    @Override
    public void runTest(final DxlClientFactory clientFactory) throws Exception {
        try (DxlClient client = clientFactory.newClientInstance();
             TestService testService = new TestService(client)) {
            final Lock responseLock = new ReentrantLock();
            final Condition responseCondition = responseLock.newCondition();
            final AtomicInteger responseCount = new AtomicInteger();
            final Set<String> outstandingRequests =
                Collections.synchronizedSet(new HashSet<>());

            client.connect();

            //
            // Create a test service that responds to requests on a particular topic.
            //
            final String topic = UuidGenerator.generateIdAsString();

            // Register the service
            final ServiceRegistrationInfo regInfo =
                new ServiceRegistrationInfo(
                    client,
                    "asyncRequestRunnerService"
                );
            regInfo.addChannel(topic, testService);
            client.registerServiceSync(regInfo, DEFAULT_TIMEOUT);

            //
            // Create and register a response callback. Once the request has been processed
            // by the service, the requesting client will receive a notification via this
            // callback. At that point, we note that the request has been responded to (remove
            // it from the set of outstanding requests), and increment the count of responses
            // we have received via the callbacks.
            //
            final ResponseCallback responseCallback = response -> {
                responseLock.lock();
                try {
                    // Remove from outstanding requests
                    outstandingRequests.remove(response.getRequestMessageId());
                    // Increment count of responses received
                    responseCount.incrementAndGet();
                    // Notify that a response has been received (are we done yet?)
                    responseCondition.signalAll();
                } finally {
                    responseLock.unlock();
                }
            };
            // Add a global response callback (not channel-specific)
            client.addResponseCallback(null, responseCallback);

            for (int i = 0; i < REQ_COUNT; i++) {
                // Send a request without specifying a callback for the current request
                // (use the global response callback)
                Request request = new Request(client, topic);
                outstandingRequests.add(request.getMessageId());
                client.asyncRequest(request);

                // Send a request with a specific callback that is to receive the response
                // The response will be received by two callbacks (the one for this call,
                // and the global one).
                request = new Request(client, topic);
                outstandingRequests.add(request.getMessageId());
                client.asyncRequest(request, responseCallback);
            }

            responseLock.lock();
            try {
                // Wait until all the responses are received via the response callbacks.
                // The "times 3" is due to the fact that 20000 requests were sent in total.
                // 20000 were handled via the global callback, an additional 10000 were handled
                // via the callback explicitly passed in the second set of requests.
                while (responseCount.get() != (REQ_COUNT * 3)) {
                    // Fail if 5 seconds passes between responses
                    if (!responseCondition.await(5000, TimeUnit.MILLISECONDS)) {
                        fail("Timed out waiting for responses.");
                    }
                }
            } finally {
                responseLock.unlock();
            }

            // Make sure there are no outstanding requests
            assertEquals(0, outstandingRequests.size());

            // Unregister the service
            client.unregisterServiceSync(regInfo, DEFAULT_TIMEOUT);
        }
    }
}
