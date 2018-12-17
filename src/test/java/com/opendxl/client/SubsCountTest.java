/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client;

import com.opendxl.client.message.Message;
import com.opendxl.client.message.Request;
import com.opendxl.client.message.Response;
import com.opendxl.client.testutil.impl.DxlClientImplFactory;
import com.opendxl.client.util.UuidGenerator;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * Test to ensure subscription counts are accurate
 */
public class SubsCountTest extends AbstractDxlTest {

    /**
     * Creates a request for subscription count
     *
     * @param client The client
     * @param topic The topic to get the subscription count for
     * @return The {@link Request} corresponding to the subscription count query
     */
    private Request createRequest(final DxlClient client, final String topic) {
        final Request req = new Request(client, "/mcafee/service/dxl/broker/subs");
        req.setPayload(("{\"topic\":\"" + topic + "\"}").getBytes());
        return req;
    }

    /**
     * Tests whether broker subscription counts are accurate
     *
     * @throws Exception If an error occurs
     */
    @Test
    public void testSubCounts() throws Exception {
        final List<DxlClient> clients = new ArrayList<>();
        try (DxlClient client = DxlClientImplFactory.getDefaultInstance().newClientInstance()) {
            client.connect();
            for (int i = 0; i < 6; i++) {
                final DxlClient c = DxlClientImplFactory.getDefaultInstance().newClientInstance();
                c.connect();
                clients.add(c);
            }

            final String random = UuidGenerator.generateIdAsString();
            final String random2 = UuidGenerator.generateIdAsString();
            final String topic1 = "/foo/bar/" + random + "/" + random2;
            clients.get(0).subscribe(topic1);
            clients.get(1).subscribe(topic1);
            clients.get(2).subscribe(topic1);
            clients.get(3).subscribe("/foo/bar/" + random + "/#");
            clients.get(4).subscribe("/foo/+/" + random + "/#");

            final String topic2 = "/foo/baz/" + random2;
            clients.get(1).subscribe(topic2);
            clients.get(2).subscribe(topic2);

            clients.get(5).subscribe("#");

            Response res = client.syncRequest(createRequest(client, topic1));
            assertTrue(new String(res.getPayload(), Message.CHARSET_UTF8).contains(":6}"));
            res = client.syncRequest(createRequest(client, topic2));
            assertTrue(new String(res.getPayload(), Message.CHARSET_UTF8).contains(":3}"));
        }
    }
}
