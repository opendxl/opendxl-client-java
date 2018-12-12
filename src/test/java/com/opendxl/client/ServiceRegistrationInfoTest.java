/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client;

import com.opendxl.client.callback.RequestCallback;
import com.opendxl.client.json.JsonRegisterService;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class ServiceRegistrationInfoTest {
    @Test
    public void testSerializeWithNoData() throws Exception {
        ServiceRegistrationInfo info = new ServiceRegistrationInfo(null, "/mcafee/service/JTI");
        JsonRegisterService registerService = new JsonRegisterService(null, info);
        String result = registerService.toJsonString();

        assertTrue(result.contains("\"serviceType\":\"/mcafee/service/JTI\""));
        assertTrue(result.contains("\"serviceGuid\":\"" + info.getServiceId() + "\""));
    }

    @Test
    public void testSerialize() throws Exception {
        ServiceRegistrationInfo info = new ServiceRegistrationInfo(null, "/mcafee/service/JTI");

        final RequestCallback requestCallback = request -> {
        };

        info.addTopic("/mcafee/service/JTI/file/reputation", requestCallback);
        info.addTopic("/mcafee/service/JTI/cert/reputation", requestCallback);

        JsonRegisterService registerService = new JsonRegisterService(null, info);
        String result = registerService.toJsonString();

        assertTrue(result.contains("\"serviceType\":\"/mcafee/service/JTI\""));
        assertTrue(result.contains("\"serviceGuid\":\"" + info.getServiceId() + "\""));
        assertTrue(result.contains("\"/mcafee/service/JTI/file/reputation\""));
        assertTrue(result.contains("\"/mcafee/service/JTI/cert/reputation\""));
    }
}