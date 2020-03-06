/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client;

import com.opendxl.client.message.Request;
import com.opendxl.client.testutil.ThreadPerRunExecutor;
import com.opendxl.client.util.UuidGenerator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * {@link DxlClient} test that creates a thread per client and sends a request to a test service. Calculations such
 * as request/second and average response time are calculated.
 *
 * @see DxlClient#syncRequest(com.opendxl.client.message.Request, long);
 */
public class SyncRequestThroughputRunner extends AbstractRunner {

    /**
     * The number of clients (thread per client)
     */
    private static final int THREAD_COUNT = 500;

    /**
     * The number of requests to send
     */
    private static final int REQUEST_COUNT = 10;

    /**
     * The maximum time for the test
     */
    private static final long MAX_TIME = 10 * 60 * 1000;

    /**
     * The maximum time to wait between connections
     */
    private static final int MAX_CONNECT_WAIT = 60 * 1000;

    /**
     * The number of times to try to connect to the broker
     */
    private static final int MAX_CONNECT_RETRIES = 10;

    /**
     * {@inheritDoc}
     */
    @Override
    public void runTest(final DxlClientFactory clientFactory) throws Exception {
        try (DxlClient serverClient = clientFactory.newClientInstance();
             TestService testService = new TestService(serverClient)) {
            final AtomicLong requestsStartTime = new AtomicLong(0);
            final AtomicLong cummulativeResponseTime = new AtomicLong(0);
            final List<Long> responseTimes = Collections.synchronizedList(new ArrayList<>());
            final AtomicInteger responseCount = new AtomicInteger(0);

            final AtomicInteger connectRetries = new AtomicInteger(0);
            final AtomicInteger connectCount = new AtomicInteger(0);
            final Lock connectLock = new ReentrantLock();
            final Condition connectCondition = connectLock.newCondition();
            final long connectTimeStart = System.currentTimeMillis();
            final AtomicLong connectTime = new AtomicLong(0);

            serverClient.connect();

            //
            // Create the test server
            //
            final String topic = UuidGenerator.generateIdAsString();
            final ServiceRegistrationInfo regInfo =
                new ServiceRegistrationInfo(
                    serverClient,
                    "syncRequestThroughputRunnerService"
                );
            regInfo.addTopic(topic, testService);
            serverClient.registerServiceSync(regInfo, DEFAULT_TIMEOUT);

            //
            // Create a thread for each request. Wait for all of the clients to connect
            // to the broker before starting to calculate response related statistics.
            //
            final ThreadPerRunExecutor executor = new ThreadPerRunExecutor(THREAD_COUNT);
            executor.execute(
                () -> {
                    try {
                        try (DxlClient client = clientFactory.newClientInstance()) {
                            //
                            // Connect to the broker with local retries
                            //   System.setProperty( DxlClient.SYSPROP_CONNECT_RETRIES, "0" );
                            //   System.setProperty( DxlClient.SYSPROP_DISABLE_DISCONNECTED_STRATEGY, "true" );
                            //
                            int retries = MAX_CONNECT_RETRIES;
                            boolean connected = false;
                            while (!connected && retries > 0) {
                                try {
                                    client.connect();
                                    connected = true;
                                } catch (Exception ex) {
                                    if (--retries > 0) {
                                        connectRetries.incrementAndGet();
                                    }
                                }
                            }
                            if (!connected) {
                                fail("Unable to connect after retries.");
                            }

                            //
                            // Wait for all clients to connect
                            //
                            connectLock.lock();
                            try {
                                connectCount.incrementAndGet();
                                connectCondition.signalAll();

                                while (connectCount.get() != THREAD_COUNT) {
                                    if (!connectCondition.await(MAX_CONNECT_WAIT, TimeUnit.MILLISECONDS)) {
                                        fail("Timeout waiting for all threads to connect");
                                    }
                                }

                                //
                                // Once all clients have connected, reset timing information
                                //
                                if (requestsStartTime.get() == 0) {
                                    requestsStartTime.set(System.currentTimeMillis());
                                    connectTime.set(requestsStartTime.get() - connectTimeStart);
                                }
                            } finally {
                                connectLock.unlock();
                            }

                            for (int i = 0; i < REQUEST_COUNT; i++) {
                                // Send the request
                                final Request req = new Request(client, topic);
                                long callStartTime = System.currentTimeMillis();

                                assertIsResponse(client.syncRequest(req));

                                int count = responseCount.incrementAndGet();
                                if (count % 100 == 0) {
                                    System.out.println(count);
                                }

                                // Calculate and track response times
                                long responseTime = System.currentTimeMillis() - callStartTime;
                                cummulativeResponseTime.addAndGet(responseTime);
                                responseTimes.add(responseTime);
                            }
                        }
                    } catch (Exception ex) {
                        System.out.println(ex.getMessage());
                    }
                }
            );

            // Wait for all requests to complete
            if (!executor.joinThreads(MAX_TIME)) {
                fail("Timeout waiting for thread to complete.");
            }

            final long endTime = System.currentTimeMillis();

            // Ensure all requests completed
            assertEquals(THREAD_COUNT, responseCount.get() / REQUEST_COUNT);

            final long totalTime = endTime - requestsStartTime.get();
            System.out.println("Connect time: " + connectTime.get() / 1000.0f);
            System.out.println("Connect retries: " + connectRetries.get());
            System.out.println("Request time: " + totalTime / 1000.0f);
            System.out.println("Total requests: " + THREAD_COUNT * REQUEST_COUNT);
            System.out.println("Requests/second: " + (THREAD_COUNT * REQUEST_COUNT) / (totalTime / 1000.0f));

            System.out.println("Average response time: "
                + (cummulativeResponseTime.get() / (float) (THREAD_COUNT * REQUEST_COUNT)) / 1000.0f);
            final int mid = ((THREAD_COUNT * REQUEST_COUNT) / 2);
            Collections.sort(responseTimes);
            //noinspection ConstantConditions
            System.out.println("Median response time: "
                + ((((THREAD_COUNT * REQUEST_COUNT) % 2 == 0)
                    ? (responseTimes.get(mid) + responseTimes.get(mid - 1)) / 2.0f
                    : responseTimes.get(mid))) / 1000.0f);

            // Unregister service
            serverClient.unregisterServiceSync(regInfo, DEFAULT_TIMEOUT);
        }
    }
}
