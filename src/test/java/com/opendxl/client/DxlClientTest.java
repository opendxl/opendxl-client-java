
/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client;

import com.opendxl.client.callback.ResponseCallback;
import com.opendxl.client.exception.DxlException;
import com.opendxl.client.message.ErrorResponse;
import com.opendxl.client.message.Event;
import com.opendxl.client.message.Request;
import com.opendxl.client.message.Response;
import com.opendxl.client.testutil.TestService;
import com.opendxl.client.testutil.impl.DxlClientImplFactory;
import com.opendxl.client.util.UuidGenerator;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Test cases for the MQTT {@link DxlClient} implementation
 */
public class DxlClientTest extends AbstractDxlTest {

    /**
     * Invoked prior to running test
     */
    @Before
    public void beforeTest() throws Exception {
        super.beforeTest();

        System.setProperty(Constants.SYSPROP_CONNECT_RETRIES, "0");
        System.setProperty(Constants.SYSPROP_DISABLE_DISCONNECTED_STRATEGY, "true");
    }

    /**
     * Returns a new {@link DxlClient} instance
     *
     * @return A new {@link DxlClient} instance
     */
    protected DxlClient newClientInstance() throws DxlException {
        return getDxlClientFactory().newClientInstance();
    }

    /**
     * Tests the connect and disconnect methods of the {@link DxlClient}
     *
     * @see DxlClient#connect()
     * @see DxlClient#disconnect()
     */
    @Test
    public void testConnectAndDisconnect() throws Exception {
        try (DxlClient client = newClientInstance()) {
            client.connect();
            client.disconnect();
        }
    }

    /**
     * Tests the subscribe and unsubscribe methods of the {@link DxlClient}
     *
     * @see DxlClient#subscribe(String)
     * @see DxlClient#unsubscribe(String)
     */
    @Test
    public void testSubscribeAndUnsubscribe() throws Exception {
        try (DxlClient client = newClientInstance()) {
            client.connect();
            final String topic = UuidGenerator.generateIdAsString();
            client.subscribe(topic);
            client.unsubscribe(topic);
        }
    }

    /**
     * Tests the asynchronous request methods of the {@link DxlClient}.
     *
     * @see AsyncRequestRunner
     */
    @Test
    public void testAsyncRequest() throws Exception {
        new AsyncRequestRunner().runTest(getDxlClientFactory());
    }

    /**
     * Tests the events-related methods of the {@link DxlClient}.
     *
     * @see EventsRunner
     */
    @Test
    public void testEvents() throws Exception {
        new EventsRunner().runTest(getDxlClientFactory());
    }

    /**
     * Tests the synchronous request methods of the {@link DxlClient}.
     *
     * @see SyncRequestRunner
     */
    @Test
    public void testSyncRequest() throws Exception {
        new SyncRequestRunner().runTest(getDxlClientFactory());
    }

    /**
     * Tests whether payloads can be successfully delivered from a client to the server.
     * Payloads are simply bytes of data that are used to provide application-specific
     * information.
     *
     * @see MessagePayloadRunner
     */
    @Test
    public void testPayload() throws Exception {
        new MessagePayloadRunner().runTest(getDxlClientFactory());
    }

    /**
     * Test to ensure that {@link ErrorResponse} messages can be successfully delivered
     * from a service to a client.
     *
     * @see ErrorResponse
     */
    @Test
    public void testErrorMessage() throws Exception {
        try (DxlClient client = newClientInstance();
             TestService testService = new TestService(client)) {
            client.connect();

            final int errorCode = 9090;
            final String errorMessage = "My error message";

            final String topic = UuidGenerator.generateIdAsString();

            //
            // Create a test service that returns error messages
            //
            final ServiceRegistrationInfo regInfo =
                new ServiceRegistrationInfo(
                    client,
                    "testErrorMessageService"
                );
            regInfo.addChannel(topic, testService);
            client.registerServiceSync(regInfo, DEFAULT_TIMEOUT);

            testService.setReturnError(true);
            testService.setErrorCode(errorCode);
            testService.setErrorMessage(errorMessage);
            client.addRequestCallback(topic, testService);

            // Send a request and ensure the response is an error message
            final Response response = client.syncRequest(new Request(client, topic));
            assertTrue("Response is not an error response", response instanceof ErrorResponse);
            assertEquals(errorCode, ((ErrorResponse) response).getErrorCode());
            assertEquals(errorMessage, ((ErrorResponse) response).getErrorMessage());

            // Unregister service
            client.unregisterServiceSync(regInfo, DEFAULT_TIMEOUT);
        }
    }

    /**
     * Test the "other fields" map
     */
    @Test
    public void testOtherFields() throws Exception {
        final int otherCount = 1000;
        try (DxlClient client = newClientInstance()) {
            client.connect();

            final Map<String, String> map = new HashMap<>();
            for (int i = 0; i < otherCount; i++) {
                map.put("key" + i, "value" + i);
            }

            final String topic = UuidGenerator.generateIdAsString();
            client.subscribe(topic);

            final Object sync = new Object();
            final Event[] receivedEvent = new Event[]{null};

            client.addEventCallback(topic,
                event -> {
                    synchronized (sync) {
                        receivedEvent[0] = event;
                        sync.notify();
                    }
                }
            );

            final Event evt = new Event(client, topic);
            evt.setOtherFields(map);
            client.sendEvent(evt);

            synchronized (sync) {
                if (receivedEvent[0] == null) {
                    sync.wait(10 * 1000);
                }
            }

            final Event theEvent = receivedEvent[0];
            if (theEvent == null) {
                fail("Event was not received.");
            } else {
                for (int i = 0; i < otherCount; i++) {
                    System.out.println(theEvent.getOtherFields().get("key" + i));

                    assertEquals("value" + i, theEvent.getOtherFields().remove("key" + i));
                }
                System.out.println(theEvent.getOtherFields());
                assertEquals(1, theEvent.getOtherFields().size());
                assertEquals(theEvent.getOtherFields().keySet().iterator().next(), "dxl.certs");
            }
        }
    }

    /**
     * {@link DxlClient} test that creates a thread per client and sends a request
     * to a test service. Calculations such as request/second and average response
     * time are calculated.
     *
     * @see SyncRequestThroughputRunner
     */
    @Test
    public void testSyncRequestThroughput() throws Exception {
        new SyncRequestThroughputRunner().runTest(getDxlClientFactory());
    }

    @Test
    public void testEventThroughput() throws Exception {
        new EventThroughputRunner().runTest(getDxlClientFactory());
    }

    /**
     * Test to ensure that synchronous requests can't be made on the incoming message thread
     */
    @Test
    public void testSyncDuringCallback() throws Exception {
        final String eventTopic = UuidGenerator.generateIdAsString();
        final String reqTopic = UuidGenerator.generateIdAsString();
        final Exception[] exception = {null};
        try (DxlClient client = newClientInstance()) {
            client.connect();
            client.subscribe(eventTopic);
            client.addEventCallback(
                eventTopic,
                event -> {
                    try {
                        final Request req = new Request(client, reqTopic);
                        client.syncRequest(req);
                    } catch (Exception ex) {
                        exception[0] = ex;
                        ex.printStackTrace();
                    }
                }
            );

            Thread.sleep(5 * 1000);

            final Event evt = new Event(client, eventTopic);
            client.sendEvent(evt);

            Thread.sleep(5 * 1000);

            assertTrue(exception[0] != null && exception[0].getMessage().contains("different thread"));
        }
    }

    /**
     * Tests threading of incoming requests
     */
    @Test
    public void testIncomingMessageThreading() throws Exception {
        final int threadCount = 10;
        System.setProperty(Constants.SYSPROP_INCOMING_MESSAGE_THREAD_POOL_SIZE, Integer.toString(threadCount));
        final Set<String> threadNames = new HashSet<>();
        try {
            final String eventTopic = UuidGenerator.generateIdAsString();
            try (DxlClient client = newClientInstance()) {
                client.connect();
                client.subscribe(eventTopic);
                client.addEventCallback(
                    eventTopic,
                    event -> {
                        synchronized (threadNames) {
                            threadNames.add(Thread.currentThread().getName());
                        }
                    }
                );

                for (int i = 0; i < 1000; i++) {
                    final Event evt = new Event(client, eventTopic);
                    client.sendEvent(evt);
                }

                Thread.sleep(10 * 1000);

                assertEquals(threadCount, threadNames.size());
                System.out.println("testIncomingMessageThreading() thread names:");
                for (String threadName : threadNames) {
                    System.out.println(threadName);
                }
            }
        } finally {
            System.setProperty(Constants.SYSPROP_INCOMING_MESSAGE_THREAD_POOL_SIZE, "1");
        }
    }

    /**
     * Test that ensures that async callbacks are being cleaned up via timeout
     */
    @Test
    public void testAsyncCallbackTimeout() throws Exception {
        System.setProperty(Constants.SYSPROP_ASYNC_CALLBACK_CHECK_INTERVAL, "10000");

        final ResponseCallback cb = response -> {
        };

        try {
            try (DxlClient client = newClientInstance()) {
                client.connect();

                final String reqTopic = UuidGenerator.generateIdAsString();
                final String missingTopic = UuidGenerator.generateIdAsString();

                final TestService testService =
                    new TestService(client) {
                        @Override
                        public void onRequest(Request request) {
                        }
                    };

                //
                // Create a test service that returns error messages
                //
                final ServiceRegistrationInfo regInfo =
                    new ServiceRegistrationInfo(
                        client,
                        "asyncCallbackTestService"
                    );
                regInfo.addChannel(reqTopic, testService);
                client.registerServiceSync(regInfo, DEFAULT_TIMEOUT);

                client.asyncRequest(new Request(client, reqTopic), cb, 60 * 1000);
                for (int i = 0; i < 10; i++) {
                    client.asyncRequest(new Request(client, reqTopic), cb, 5 * 1000);
                }

                // Use missing topic to get an error response
                client.asyncRequest(new Request(client, missingTopic));

                assertEquals(11, ((DxlClient) client).getAsyncCallbackCount());
                System.out.println("asyncCallbackCount=" + ((DxlClient) client).getAsyncCallbackCount());

                for (int i = 0; i < 20; i++) {
                    System.out.println("asyncCallbackCount=" + ((DxlClient) client).getAsyncCallbackCount());
                    Thread.sleep(1000);
                    // Use missing topic to get an error response
                    client.asyncRequest(new Request(client, missingTopic));
                }

                assertEquals(1, ((DxlClient) client).getAsyncCallbackCount());
            }
        } finally {
            System.setProperty(Constants.SYSPROP_ASYNC_CALLBACK_CHECK_INTERVAL, Integer.toString(5 * 60 * 1000));
        }
    }

    private DxlClientFactory getDxlClientFactory() {
        return DxlClientImplFactory.getDefaultInstance();
    }
}
