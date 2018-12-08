/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client;

import com.opendxl.client.message.Message;
import com.opendxl.client.message.Request;
import com.opendxl.client.message.Response;
import com.opendxl.client.testutil.impl.DxlClientImplFactory;
import org.junit.Before;
import org.junit.Test;

/**
 * Test the broker registry query
 */
public class BrokerRegistryQueryTest extends AbstractDxlTest {
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
     * See class level comment
     */
    @Test
    public void testBrokerRegistryQuery() throws Exception {
        try (DxlClient client = DxlClientImplFactory.getDefaultInstance().newClientInstance()) {
            client.connect();

            final Request req = new Request(client, "/mcafee/service/dxl/brokerregistry/query");
            req.setPayload("{}".getBytes(Message.CHARSET_UTF8));
            final Response response = client.syncRequest(req);
            assertIsResponse(response);
            System.out.println("## sourceBrokerGuid: " + response.getSourceBrokerGuid());
            System.out.println("## sourceClientGuid: " + response.getSourceClientId());
            System.out.println(
                new String(response.getPayload(), Message.CHARSET_UTF8));
        }
    }
}
