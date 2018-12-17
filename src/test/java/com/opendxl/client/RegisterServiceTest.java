/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client;

import com.opendxl.client.callback.RequestCallback;
import com.opendxl.client.exception.DxlException;
import com.opendxl.client.message.Request;
import com.opendxl.client.message.Response;
import com.opendxl.client.testutil.impl.DxlClientImplFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests the registration of DXL services
 */
public class RegisterServiceTest extends AbstractDxlTest {
    /** Delay after performing operation */
    private static final long POST_OP_DELAY = 1000L;
    /** Wait time for registration/unregistration */
    private static final long REG_DELAY = 60 * 1000L;

    /** The client */
    DxlClient client = null;

    /** The registration callback */
    private final EventCounterCallback registerCallback = new EventCounterCallback();
    /** The unregistration callback */
    private final EventCounterCallback unregisterCallback = new EventCounterCallback();

    /** The service registration */
    private ServiceRegistrationInfo info = null;

    /** The request callback */
    @SuppressWarnings("FieldCanBeLocal")
    private RequestCallback requestCallback = null;

    /** {@inheritDoc} */
    @Before
    public void beforeTest() throws Exception {
        super.beforeTest();

        System.setProperty(Constants.SYSPROP_DEFAULT_WAIT, Long.toString(5 * 1000));
        client = DxlClientImplFactory.getDefaultInstance().newClientInstance();

        registerCallback.reset();
        unregisterCallback.reset();

        client.addEventCallback(Constants.DXL_SERVICE_REGISTER_TOPIC, registerCallback);
        client.addEventCallback(Constants.DXL_SERVICE_UNREGISTER_TOPIC, unregisterCallback);

        requestCallback = request -> {
            System.out.println(request.getDestinationTopic());
            System.out.println(new String(request.getPayload()));

            Response response = new Response(client, request);
            response.setPayload("Ok".getBytes());
            try {
                client.sendResponse(response);
            } catch (DxlException ex) {
                System.out.println("Failed to send response" + ex);
            }
        };

        System.setProperty(Constants.SYSPROP_SERVICE_TTL_RESOLUTION, "sec");
        System.setProperty(Constants.SYSPROP_SERVICE_TTL_LOWER_LIMIT, Long.toString(1));

        info = new ServiceRegistrationInfo(client, "/mcafee/service/JTI");

        info.addTopic("/mcafee/service/JTI/file/reputation/" + info.getServiceId(), requestCallback);
        info.addTopic("/mcafee/service/JTI/cert/reputation/" + info.getServiceId(), requestCallback);
    }

    /** {@inheritDoc} */
    @After
    public void afterTest() throws Exception {
        try {
            client.close();
        } finally {
            client = null;
        }
    }

    /**
     * Tests registering (async) a service prior to connect
     *
     * @throws Exception If an error occurs
     */
    @Test
    public void testRegisterServiceBeforeConnect() throws Exception {
        client.registerServiceAsync(info);
        Thread.sleep(POST_OP_DELAY);
        client.connect();
        Thread.sleep(POST_OP_DELAY);
        client.unregisterServiceSync(info, REG_DELAY);
        Thread.sleep(POST_OP_DELAY);

        assertEquals(1, registerCallback.get());
        assertEquals(1, unregisterCallback.get());
    }

    /**
     * Tests registering a service after connect
     *
     * @throws Exception If an error occurs
     */
    @Test
    public void testRegisterServiceAfterConnect() throws Exception {
        client.connect();
        Thread.sleep(POST_OP_DELAY);
        client.registerServiceSync(info, REG_DELAY);
        client.unregisterServiceSync(info, REG_DELAY);
        Thread.sleep(POST_OP_DELAY);

        assertEquals(1, registerCallback.get());
        assertEquals(1, unregisterCallback.get());
    }

    /**
     * Tests registering (async) a service but never connecting
     *
     * @throws Exception If an error occurs
     */
    @Test
    public void testRegisterServiceNeverConnect() throws Exception {
        client.registerServiceAsync(info);
        Thread.sleep(POST_OP_DELAY);
        client.unregisterServiceAsync(info);
        Thread.sleep(POST_OP_DELAY);

        assertEquals(0, registerCallback.get());
        assertEquals(0, unregisterCallback.get());
    }

    /**
     * Tests registering (async) and unregistering a service before connecting
     *
     * @throws Exception If an error occurs
     */
    @Test
    public void testRegisterUnregisterServiceBeforeConnect() throws Exception {
        client.registerServiceAsync(info);
        Thread.sleep(POST_OP_DELAY);
        client.unregisterServiceAsync(info);
        Thread.sleep(POST_OP_DELAY);
        client.connect();
        Thread.sleep(POST_OP_DELAY);

        assertEquals(0, registerCallback.get());
        assertEquals(0, unregisterCallback.get());
    }

    /**
     * Tests registering a service and sending a request
     *
     * @throws Exception If an error occurs
     */
    @Test
    public void testRegisterServiceAndSendRequest() throws Exception {
        client.registerServiceAsync(info);
        Thread.sleep(POST_OP_DELAY);
        client.connect();
        Thread.sleep(POST_OP_DELAY);

        Request request = new Request(client,
            "/mcafee/service/JTI/file/reputation/" + info.getServiceId());
        request.setPayload("Test".getBytes());

        Response response = client.syncRequest(request);
        System.out.println(new String(response.getPayload()));

        assertEquals("Ok", new String(response.getPayload()));

        client.unregisterServiceSync(info, REG_DELAY);
    }
}
