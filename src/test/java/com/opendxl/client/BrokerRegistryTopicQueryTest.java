/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client;

import com.opendxl.client.exception.DxlException;
import com.opendxl.client.message.ErrorResponse;
import com.opendxl.client.message.Message;
import com.opendxl.client.message.Request;
import com.opendxl.client.message.Response;
import com.opendxl.client.testutil.impl.DxlClientImplFactory;
import com.opendxl.client.util.UuidGenerator;
import org.junit.Before;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.util.HashSet;
import java.util.Set;

import static java.util.Collections.EMPTY_SET;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test the Broker Registry Topic Query - Test that brokers are storing topics that have subscriptions.
 * The storing of topics that have subscriptions is part of enabling topic based routing.
 * <p>
 * <b>NOTE</b>: Besides the tests that run during a build there are also tests in this class that test topic
 * subscription addition, removal, and transfer between multiple brokers that must be run manually.
 * <p>
 */
public class BrokerRegistryTopicQueryTest extends AbstractDxlTest {
    private static final String BROKERTOPIC_QUERY_TOPIC = "/mcafee/service/dxl/brokerregistry/topicquery";

    //Broker Registry Topic Query will by default use the GUID of the broker the client is connected. Change
    // this to a specific value if you want to query for a different broker GUID.
    private static final String BROKER1_GUID = "";
    private static final String BROKER2_GUID = ""; //Used for manual testing. Put GUID of second broker here.
    private static final String BROKER1_HOST = ""; //Used for manual testing. The ip and port a broker
    private static final String BROKER2_HOST = ""; //Used for manual testing. The ip and port a broker

    //TODO add test for disconnect. for disconnect have two clients and have second one disconnect

    /**
     * Prior to running test
     */
    @Before
    public void beforeTest() throws Exception {
        super.beforeTest();

        // Wait a minute before each test...
        System.out.println("Waiting...");
        Thread.sleep(60 * 1000);
    }

    /**
     * Test a variety of single topic use cases for a broker that has at most one subscription
     */
    @Test
    public void testSingleTopic() throws Exception {
        final String topic = UuidGenerator.generateIdAsString();

        try (DxlClient client = DxlClientImplFactory.getDefaultInstance().newClientInstance()) {
            client.connect();

            System.out.println("## Get initial subscription count");
            @SuppressWarnings("unchecked") final int initialSubscriptionCount = sendBrokerTopicQuery(
                client, null, BROKER1_GUID, EMPTY_SET).getCount();

            client.subscribe(topic);
            System.out.println("## testSingleTopic topic = " + topic);

            final int expectedSubscriptionCount = 1 + initialSubscriptionCount;

            final Set<String> topics = new HashSet<>();

            //Query with no topics
            //noinspection unchecked
            validateBrokerTopicQueryResponse(sendBrokerTopicQuery(client, null, BROKER1_GUID, EMPTY_SET),
                expectedSubscriptionCount, true);

            //Query on non existent topic
            topics.add("/foo/bar/baz");
            validateBrokerTopicQueryResponse(sendBrokerTopicQuery(client, null, BROKER1_GUID, topics),
                expectedSubscriptionCount, false);

            //Query on one existing topic
            topics.clear();
            topics.add(topic);
            validateBrokerTopicQueryResponse(sendBrokerTopicQuery(client, null, BROKER1_GUID, topics),
                expectedSubscriptionCount, true);

            //Query on existing topic and non-existing topic
            topics.clear();
            topics.add(topic);
            topics.add("not/gonna/find/it");
            validateBrokerTopicQueryResponse(sendBrokerTopicQuery(client, null, BROKER1_GUID, topics),
                expectedSubscriptionCount, false);

            //Query on non existing broker guid
            topics.clear();
            topics.add(topic);
            validateBrokerTopicQueryResponse(sendBrokerTopicQuery(client, null, "{1234}", topics), 0, false);

            //Query on topic after unsubscribe with topic
            client.unsubscribe(topic);
            topics.clear();
            topics.add(topic);
            validateBrokerTopicQueryResponse(sendBrokerTopicQuery(client, null, BROKER1_GUID, topics),
                initialSubscriptionCount, false);

            //subscribe to wild card topic
            client.subscribe("/foo/#");

            //query on random topic, should match since client is subscribed to general wild card
            topics.clear();
            //topics.add( topic );
            topics.add("/foo/#");
            System.out.println("## testSingleTopic subscribed to /foo/#");
            validateBrokerTopicQueryResponse(sendBrokerTopicQuery(client, null, BROKER1_GUID, topics),
                expectedSubscriptionCount, true);

            client.unsubscribe("/foo/#");
        }
    }

    /**
     * Test the broker topic query with 1000 subscriptions
     */
    @Test
    public void testMultipleTopics() throws Exception {
        try (DxlClient client = DxlClientImplFactory.getDefaultInstance().newClientInstance()) {
            client.connect();

            System.out.println("## Get initial subscription count");
            @SuppressWarnings("unchecked") final int initialSubscriptionCount =
                sendBrokerTopicQuery(client, null, BROKER1_GUID, EMPTY_SET).getCount();

            final Set<String> topics = new HashSet<>();
            for (int i = 0; i < 1000; i++) {
                String topic = "/test/" + UuidGenerator.generateIdAsString();
                client.subscribe(topic);
                topics.add(topic);
            }

            final int expectedSubscriptionCount = 1000 + initialSubscriptionCount;

            validateBrokerTopicQueryResponse(sendBrokerTopicQuery(client, null, BROKER1_GUID, topics),
                expectedSubscriptionCount, true);

            //TODO should do a clear here on the topics and then query? seems like a lot of data to send across
            topics.add("/foo");
            validateBrokerTopicQueryResponse(sendBrokerTopicQuery(client, null, BROKER1_GUID, topics),
                expectedSubscriptionCount, false);

            for (String topic : topics) {
                client.unsubscribe(topic);
            }
            topics.clear();
            validateBrokerTopicQueryResponse(sendBrokerTopicQuery(client, null, BROKER1_GUID, topics),
                initialSubscriptionCount, true);
        }
    }

    /**
     * Test that the broker cleans up topic subscriptions appropriately when a client disconnects.
     */
    @Test
    public void testSubscriptionCountAfterDisconnect() throws Exception {
        final String topic = UuidGenerator.generateIdAsString();
        final Set<String> topics = new HashSet<>();
        topics.add(topic);

        try (DxlClient client1 = DxlClientImplFactory.getDefaultInstance().newClientInstance()) {
            client1.connect();

            System.out.println("## Get initial subscription count");
            @SuppressWarnings("unchecked") final int initialSubscriptionCount =
                sendBrokerTopicQuery(client1, null, BROKER1_GUID, EMPTY_SET).getCount();

            //Connect with second client
            try (DxlClient client2 = DxlClientImplFactory.getDefaultInstance().newClientInstance()) {
                client2.connect();

                client2.subscribe(topic);
                final int expectedSubscriptionCount = 1 + initialSubscriptionCount;
                System.out.println("## Subscribe to topic " + topic
                    + ". expectedSubscriptionCount:" + expectedSubscriptionCount);
                validateBrokerTopicQueryResponse(sendBrokerTopicQuery(client2, null, BROKER1_GUID, topics),
                    expectedSubscriptionCount, true);
            }

            Thread.sleep(15 * 1000L); //sleep 15 seconds to allow broker to clean up after client2 disconnects

            validateBrokerTopicQueryResponse(sendBrokerTopicQuery(client1, null, BROKER1_GUID, topics),
                initialSubscriptionCount, false);
        }
    }

    /**
     * A method to validate a {@link BrokerTopicQueryResponse} object
     *
     * @param brokerTopicQueryResponse The broker topic query response object to validate
     * @param expectedTopicCount The expected count of topics
     * @param expectedTopicsExist The expected indicator if topics exist or not
     */
    private void validateBrokerTopicQueryResponse(
        BrokerTopicQueryResponse brokerTopicQueryResponse, int expectedTopicCount, boolean expectedTopicsExist) {
        assertEquals("Topic count does not equal expected", expectedTopicCount, brokerTopicQueryResponse.getCount());
        assertEquals("Topics exist does not equal expected", expectedTopicsExist, brokerTopicQueryResponse.isExists());
    }

    /**
     * A method to send a broker topic query. Returns the response. This method also validates if the response message
     * came from one of the specified target brokers.
     *
     * @param client The dxl client
     * @param targetBrokers The brokers to send the query to
     * @param brokerGuid The broker to query on
     * @param topics The topics to query on
     * @return The response from a broker topic query
     */
    private BrokerTopicQueryResponse sendBrokerTopicQuery(DxlClient client, Set<String> targetBrokers,
                                                          String brokerGuid, Set<String> topics)
        throws DxlException, UnsupportedEncodingException {
        Request request = new Request(client, BROKERTOPIC_QUERY_TOPIC);

        if (targetBrokers != null && !targetBrokers.isEmpty()) {
            request.setBrokerIds(targetBrokers);
        }

        BrokerTopicQueryRequest brokerTopicQueryRequest = new BrokerTopicQueryRequest();
        brokerTopicQueryRequest.setBrokerGuid(brokerGuid);
        brokerTopicQueryRequest.setTopics(topics);

        request.setPayload(JsonUtils.toString(brokerTopicQueryRequest).getBytes(Message.CHARSET_UTF8));

        final Response response = client.syncRequest(request);

        if (response instanceof ErrorResponse) {
            System.out.println("## Error Response: " + ((ErrorResponse) response).getErrorMessage());
        }

        assertIsResponse(response);

        //Validate response came from a target broker
        if (targetBrokers != null && !targetBrokers.isEmpty()) {
            assertTrue("Broker Source guid did not match input target brokers",
                targetBrokers.contains(response.getSourceBrokerId()));
        }

        System.out.println("## response.getSourceBrokerGuid: " + response.getSourceBrokerId());

        String responsePayload = new String(response.getPayload(), Message.CHARSET_UTF8);
        System.out.println("## responsePayload: " + responsePayload);

        return JsonUtils.fromString(responsePayload, BrokerTopicQueryResponse.class);
    }

    /**
     * Request class for a Broker Topic Query
     */
    private static class BrokerTopicQueryRequest extends AbstractJsonMessage {
        /* The broker guid to query on */
        private String brokerGuid;
        /* The topics to query on */
        private Set<String> topics;

        public String getBrokerGuid() {
            return this.brokerGuid;
        }

        @SuppressWarnings("WeakerAccess")
        public void setBrokerGuid(String brokerGuid) {
            this.brokerGuid = brokerGuid;
        }

        public Set<String> getTopics() {
            return this.topics;
        }

        @SuppressWarnings("WeakerAccess")
        public void setTopics(Set<String> topics) {
            this.topics = topics;
        }
    }

    /**
     * Response class for the Broker Topic Query
     */
    private static class BrokerTopicQueryResponse {
        /* The total count of topics */
        private int count;
        /* Indicator if queried on topics exist or not */
        private boolean exists;

        public int getCount() {
            return this.count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        @SuppressWarnings("WeakerAccess")
        public boolean isExists() {
            return this.exists;
        }

        public void setExists(boolean exists) {
            this.exists = exists;
        }
    }
}
