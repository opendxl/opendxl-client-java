/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client;

import com.opendxl.client.message.Event;
import com.opendxl.client.testutil.ThreadPerRunExecutor;
import com.opendxl.client.util.UuidGenerator;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Measures the throughput of DXL events
 */
public class EventThroughputRunner extends AbstractRunner {
    /**
     * The number of threads (clients)
     */
    private static final int THREAD_COUNT = 1000;

    /**
     * The number of events to send (per thread)
     */
    private static final int EVENT_COUNT = 100;

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
        try (DxlClient sendClient = clientFactory.newClientInstance()) {
            final AtomicLong requestsStartTime = new AtomicLong(0);

            final Lock eventCountLock = new ReentrantLock();
            final Condition eventCountCondition = eventCountLock.newCondition();

            final AtomicInteger eventCount = new AtomicInteger(0);

            final AtomicInteger connectRetries = new AtomicInteger(0);
            final AtomicInteger connectCount = new AtomicInteger(0);
            final Lock connectLock = new ReentrantLock();
            final Condition connectCondition = connectLock.newCondition();
            final long connectTimeStart = System.currentTimeMillis();
            final AtomicLong connectTime = new AtomicLong(0);

            sendClient.connect();

            //
            // Create the test server
            //
            final String eventTopic = UuidGenerator.generateIdAsString();

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

                            client.addEventCallback(eventTopic,
                                event -> {
                                    eventCountLock.lock();
                                    try {
                                        if ((eventCount.incrementAndGet() % 1000) == 0) {
                                            System.out.println(client.getUniqueId() + ": " + eventCount.get()
                                                + ": " + new String(event.getPayload()));
                                        }

                                        //eventCount.incrementAndGet();
                                        eventCountCondition.signalAll();
                                    } finally {
                                        eventCountLock.unlock();
                                    }
                                }
                            );

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

                                    for (int i = 0; i < EVENT_COUNT; i++) {
                                        final Event e = new Event(sendClient, eventTopic);
                                        System.out.println("### send: " + i);
                                        e.setPayload(("" + i).getBytes());
                                        sendClient.sendEvent(e);
                                    }
                                }
                            } finally {
                                connectLock.unlock();
                            }


                            eventCountLock.lock();
                            try {
                                while (eventCount.get() != (EVENT_COUNT * THREAD_COUNT)) {
                                    eventCountCondition.await();
                                }
                            } finally {
                                eventCountLock.unlock();

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

            // Ensure all events were received
            assertEquals(EVENT_COUNT * THREAD_COUNT, eventCount.get());

            final long totalTime = endTime - requestsStartTime.get();
            System.out.println("Connect time: " + connectTime.get() / 1000.0f);
            System.out.println("Connect retries: " + connectRetries.get());
            System.out.println("Total events: " + EVENT_COUNT);
            System.out.println("Events/second: " + (EVENT_COUNT) / (totalTime / 1000.0f));
            System.out.println("Total events received: " + EVENT_COUNT * THREAD_COUNT);
            System.out.println("Total events received/second: " + (EVENT_COUNT * THREAD_COUNT) / (totalTime / 1000.0f));
            System.out.println("Elapsed time: " + (totalTime / 1000.0f));
        }
    }
}
