/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client;

import com.opendxl.client.callback.EventCallback;
import com.opendxl.client.message.Event;
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
 * Tests the events-related methods of the {@link DxlClient}.
 * <p/>
 * This test sends events through the message broker and ensures that they arrive successfully
 * at a client that is listening for them.
 *
 * @see DxlClient#sendEvent(com.opendxl.client.message.Event)
 * @see DxlClient#addEventCallback(String, com.opendxl.client.callback.EventCallback)
 */
public class EventsRunner extends AbstractRunner {
    /**
     * The number of events to send
     */
    private static final int EVENT_COUNT = 10000;

    /**
     * {@inheritDoc}
     */
    @Override
    public void runTest(final DxlClientFactory clientFactory) throws Exception {
        try (DxlClient client = clientFactory.newClientInstance()) {
            final Lock eventLock = new ReentrantLock();
            final Condition eventCondition = eventLock.newCondition();
            final AtomicInteger eventCount = new AtomicInteger();
            final Set<String> outstandingEvents =
                Collections.synchronizedSet(new HashSet<>());

            client.connect();

            // Subscribe to the appropriate event channel
            final String topic = UuidGenerator.generateIdAsString();

            //
            // Create and register an event callback. Ensure that all sent events are received
            // (via the outstanding events set). Also, track the number of total events received.
            //
            final EventCallback eventCallback =
                event -> {
                    eventLock.lock();
                    try {
                        eventCount.incrementAndGet();
                        outstandingEvents.remove(event.getMessageId());
                        eventCondition.signalAll();
                    } finally {
                        eventLock.unlock();
                    }

                };
            client.addEventCallback(topic, eventCallback);

            // Send the events
            for (int i = 0; i < EVENT_COUNT; i++) {
                final Event event = new Event(client, topic);
                outstandingEvents.add(event.getMessageId());
                client.sendEvent(event);
            }

            eventLock.lock();
            try {
                // Wait for all the events to be received
                while (eventCount.get() != EVENT_COUNT) {
                    // Fail if 5 seconds passes between receiving an event
                    if (!eventCondition.await(5000, TimeUnit.MILLISECONDS)) {
                        fail("Timed out waiting for events.");
                    }
                }
            } finally {
                eventLock.unlock();
            }

            // Ensure all of the events that were sent were received
            assertEquals(0, outstandingEvents.size());
        }
    }
}