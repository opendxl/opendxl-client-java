/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client;

import com.opendxl.client.callback.EventCallback;
import com.opendxl.client.message.Event;
import com.opendxl.client.message.Response;
import com.opendxl.client.testutil.impl.DxlClientImplFactory;
import com.opendxl.client.util.UuidGenerator;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test for wildcarding support
 */
public class WildcardTest extends AbstractDxlTest {

    /**
     * Worker to ensure performance of broker does not degrade when subscribing to topic (with a lot of subscriptions)
     * using wildcards.
     */
    private long measurePerformance(
        final DxlClient client, final boolean withWildcard, final boolean topicExists
    )
        throws Exception {
        final int subCount = 10000;
        final int queryMultiplier = 10;
        final String topicPrefix = "/topic/" + UuidGenerator.generateIdAsString() + "/";
        final AtomicInteger eventCount = new AtomicInteger(0);
        final Set<String> messageIds = new HashSet<>();
        final String payload = UuidGenerator.generateIdAsString();

        final EventCallback cb =
            event -> {
                if (new String(event.getPayload()).equals(payload)) {
                    synchronized (messageIds) {
                        eventCount.incrementAndGet();
                        messageIds.add(event.getMessageId());
                        messageIds.notify();
                        if (messageIds.size() % subCount == 0) {
                            System.out.println("Messages size: " + messageIds.size());
                        }
                    }
                }
            };

        client.addEventCallback("#", cb, false);
        if (withWildcard) {
            client.subscribe(topicPrefix + "#");
        }

        for (int i = 0; i < subCount; i++) {
            if (i % 1000 == 0) {
                System.out.println("Subscribed: " + i);
            }
            client.subscribe(topicPrefix + i);
        }

        System.out.println("Subscribed.");

        long startTime = System.currentTimeMillis();
        for (int j = 0; j < subCount * queryMultiplier; j++) {
            final Event evt =
                new Event(client,
                    topicPrefix + (j % subCount + (!topicExists ? subCount : 0)));
            evt.setPayload(payload.getBytes());
            client.sendEvent(evt);
        }

        synchronized (messageIds) {
            while ((messageIds.size() != (subCount * queryMultiplier))
                || (eventCount.get() != (subCount * queryMultiplier * (withWildcard && topicExists ? 2 : 1)))) {
                messageIds.wait(DEFAULT_TIMEOUT);
            }
        }

        assertEquals((subCount * queryMultiplier), messageIds.size());
        assertEquals((subCount * queryMultiplier * (withWildcard && topicExists ? 2 : 1)), eventCount.get());

        return System.currentTimeMillis() - startTime;
    }

    /**
     * Test to ensure performance of broker does not degrade when subscribing to topic (with a lot of subscriptions)
     * using wildcards.
     */
    @Test
    public void testWildcardPerformance() throws Exception {
        try (DxlClient client = DxlClientImplFactory.getDefaultInstance().newClientInstance()) {
            client.connect();

            final long withWildcard = measurePerformance(client, true, false);
            final long withoutWildcard = measurePerformance(client, false, true);
            final long withWildcardTopicExists = measurePerformance(client, true, true);
            System.out.println("withoutWildcard: " + withoutWildcard / 1000.0);
            System.out.println("withWildcard: " + withWildcard / 1000.0);
            System.out.println("withWildcardTopicExists (x2): " + withWildcardTopicExists / 1000.0);

            assertTrue(withWildcard < (2 * withoutWildcard));
        }
    }

    /**
     * Test the wildcarding iteration (utility method)
     */
    @Test
    public void testWildcardCallback() {
        final List<String> wildcards = new ArrayList<>();
        final DxlUtils.WildcardCallback cb = wildcards::add;

        wildcards.clear();
        DxlUtils.iterateWildcards(cb, "/foo/bar");
        assertEquals(3, wildcards.size());
        assertEquals("/foo/#", wildcards.get(0));
        assertEquals("/#", wildcards.get(1));
        assertEquals("#", wildcards.get(2));

        wildcards.clear();
        DxlUtils.iterateWildcards(cb, "/foo/bar/baz");
        assertEquals(4, wildcards.size());
        assertEquals("/foo/bar/#", wildcards.get(0));
        assertEquals("/foo/#", wildcards.get(1));
        assertEquals("/#", wildcards.get(2));
        assertEquals("#", wildcards.get(3));

        wildcards.clear();
        DxlUtils.iterateWildcards(cb, "/foo/bar/baz/");
        assertEquals(5, wildcards.size());
        assertEquals("/foo/bar/baz/#", wildcards.get(0));
        assertEquals("/foo/bar/#", wildcards.get(1));
        assertEquals("/foo/#", wildcards.get(2));
        assertEquals("/#", wildcards.get(3));
        assertEquals("#", wildcards.get(4));

        wildcards.clear();
        DxlUtils.iterateWildcards(cb, "/#");
        assertEquals(1, wildcards.size());
        assertEquals("#", wildcards.get(0));

        wildcards.clear();
        DxlUtils.iterateWildcards(cb, "/");
        assertEquals(2, wildcards.size());
        assertEquals("/#", wildcards.get(0));
        assertEquals("#", wildcards.get(1));

        wildcards.clear();
        DxlUtils.iterateWildcards(cb, "#");
        assertEquals(0, wildcards.size());

        wildcards.clear();
        DxlUtils.iterateWildcards(cb, "");
        assertEquals(1, wildcards.size());
        assertEquals("#", wildcards.get(0));
    }

    /**
     * Test service-based wilcarding
     * Test the ability to transform events into requests
     * Test wildcarding of events
     */
    @Test
    public void testWildcardServices() throws Exception {
        try (DxlClient client = DxlClientImplFactory.getDefaultInstance().newClientInstance()) {
            // The request message that the service receives
            final String[] serviceRequestMessage = {null};
            // The request message corresponding to the response received by the client
            final String[] clientResponseMessageRequest = {null};
            // The event that we received
            final String[] clientEventMessage = {null};
            // The payload that the service receives
            final String[] serviceRequestMessageReceivedPayload = {null};

            // Connect
            client.connect();

            // Service registartion
            final ServiceRegistrationInfo info = new ServiceRegistrationInfo(client, "myWildCardService");
            final Map<String, String> meta = new HashMap<>();
            // Transform events mapped to "test/#" to "/request/test/..."
            meta.put("eventToRequestTopic", "/test/#");
            meta.put("eventToRequestPrefix", "/request");
            info.setMetadata(meta);

            // Request handler for the service
            info.addTopic("/request/test/#",
                request -> {
                    System.out.println(
                        "## Request in service: " + request.getDestinationTopic() + ", "
                            + request.getMessageId());
                    System.out.println("## Request in service - payload: "
                        + new String(request.getPayload()));

                    serviceRequestMessage[0] = request.getMessageId();
                    serviceRequestMessageReceivedPayload[0] = new String(request.getPayload());

                    try {
                        Response response = new Response(client, request);
                        response.setPayload(("Request response - Event payload: "
                            + new String(request.getPayload())).getBytes());
                        client.sendResponse(response);
                    } catch (final Exception ex) {
                        ex.printStackTrace();
                    }
                }
            );

            // Register the service
            client.registerServiceSync(info, 10 * 1000);

            try {
                // The event that we are going to send
                final Event evt = new Event(client, "/test/bar");

                // Event callback and subscription
                client.subscribe("/test/#");
                client.addEventCallback("/test/#",
                    event -> {
                        System.out.println("## receivedEvent: "
                            + event.getDestinationTopic() + ", " + event.getMessageId());
                        clientEventMessage[0] = event.getMessageId();
                    },
                    false
                );

                // Send our event
                System.out.println("## Sending event: " + evt.getDestinationTopic() + ", " + evt.getMessageId());
                evt.setPayload("Unit test payload".getBytes());
                client.sendEvent(evt);

                Thread.sleep(10 * 1000);

                // Make sure the service received the request properly
                assertEquals(evt.getMessageId(), serviceRequestMessage[0]);
                // Make sure the service received the request payload from the event properly
                assertEquals(new String(evt.getPayload()), serviceRequestMessageReceivedPayload[0]);
                // Make sure we received the correct event
                assertEquals(evt.getMessageId(), clientEventMessage[0]);
            } finally {
                client.unregisterServiceSync(info, 10 * 1000);
            }
        }
    }
}
