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

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RegisterServiceTest extends AbstractDxlTest {
    private static final long POST_OP_DELAY = 1000L;
    private static final long REG_DELAY = 60 * 1000L;

    DxlClient client = null;

    private final EventCounterCallback registerCallback = new EventCounterCallback();
    private final EventCounterCallback unregisterCallback = new EventCounterCallback();

    private ServiceRegistrationInfo info = null;
    @SuppressWarnings("FieldCanBeLocal")
    private RequestCallback requestCallback = null;

    @Before
    public void beforeTest() throws Exception {
        super.beforeTest();

        System.setProperty(Constants.SYSPROP_DEFAULT_WAIT, Long.toString(5 * 1000));

        client = DxlClientImplFactory.getDefaultInstance().newClientInstance();

        registerCallback.reset();
        unregisterCallback.reset();

        client.addEventCallback(Constants.DXL_SERVICE_REGISTER_CHANNEL, registerCallback);
        client.addEventCallback(Constants.DXL_SERVICE_UNREGISTER_CHANNEL, unregisterCallback);

        client.subscribe(Constants.DXL_SERVICE_REGISTER_CHANNEL);
        client.subscribe(Constants.DXL_SERVICE_UNREGISTER_CHANNEL);

        requestCallback = new RequestCallback() {
            @Override
            public void onRequest(final Request request) {
                System.out.println(request.getDestinationChannel());
                System.out.println(new String(request.getPayload()));

                Response response = new Response(client, request);
                response.setPayload("Ok".getBytes());
                try {
                    client.sendResponse(response);
                } catch (DxlException ex) {
                    System.out.println("Failed to send response" + ex);
                }
            }
        };

        System.setProperty(Constants.SYSPROP_SERVICE_TTL_RESOLUTION, "sec");
        System.setProperty(Constants.SYSPROP_SERVICE_TTL_LOWER_LIMIT, Long.toString(1));

        info = new ServiceRegistrationInfo(client, "/mcafee/service/JTI");

        info.addChannel("/mcafee/service/JTI/file/reputation/" + info.getServiceGuid(), requestCallback);
        info.addChannel("/mcafee/service/JTI/cert/reputation/" + info.getServiceGuid(), requestCallback);
    }

    @After
    public void afterTest() throws Exception {
        try {
            client.close();
        } finally {
            client = null;
        }
    }

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

    @Test
    public void testRegisterServiceNeverConnect() throws Exception {
        client.registerServiceAsync(info);
        Thread.sleep(POST_OP_DELAY);
        client.unregisterServiceAsync(info);
        Thread.sleep(POST_OP_DELAY);

        assertEquals(0, registerCallback.get());
        assertEquals(0, unregisterCallback.get());
    }

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

    @Test
    public void testRegisterServiceAndSendRequest() throws Exception {
        client.registerServiceAsync(info);
        Thread.sleep(POST_OP_DELAY);
        client.connect();
        Thread.sleep(POST_OP_DELAY);

        Request request = new Request(client,
            "/mcafee/service/JTI/file/reputation/" + info.getServiceGuid());
        request.setPayload("Test".getBytes());

        Response response = client.syncRequest(request);
        System.out.println(new String(response.getPayload()));

        assertEquals("Ok", new String(response.getPayload()));

        client.unregisterServiceSync(info, REG_DELAY);
    }

    // @Test This is removed in other branches
    public void testRegisterServiceWeakReferenceBeforeConnect() throws Exception {
        ReferenceQueue<ServiceRegistrationInfo> queue = new ReferenceQueue<>();
        WeakReference<ServiceRegistrationInfo> ref = new WeakReference<>(info, queue);

        client.registerServiceAsync(info);

        // Deleted the service registration
        info = null;
        // Enforce garbage collection
        System.gc();
        // Run finalization to be sure that the reference is enqueued
        System.runFinalization();
        // Weak reference should now be enqueued
        assertTrue(ref.isEnqueued());
        // Weak reference should now be null
        assertNull(ref.get());

        client.connect();
        Thread.sleep(POST_OP_DELAY);

        client.disconnect();
        Thread.sleep(POST_OP_DELAY);

        assertEquals(0, registerCallback.get());
        assertEquals(0, unregisterCallback.get());
    }

    //@Test
    public void testRegisterServiceWeakReferenceAfterConnect() throws Exception {
        ReferenceQueue<ServiceRegistrationInfo> queue = new ReferenceQueue<>();
        WeakReference<ServiceRegistrationInfo> ref = new WeakReference<>(info, queue);

        client.registerServiceAsync(info);

        client.connect();
        Thread.sleep(POST_OP_DELAY);

        // Deleted the service registration
        info = null;
        // Enforce garbage collection
        System.gc();
        // Run finalization to be sure that the reference is enqueued
        System.runFinalization();
        // Weak reference should now be enqueued
        assertTrue(ref.isEnqueued());
        // Weak reference should now be null
        assertNull(ref.get());

        client.disconnect();
        Thread.sleep(POST_OP_DELAY);

        assertEquals(1, registerCallback.get());

        // TODO: Sometimes the unregister event does not get send; don't check for now
        // assertEquals( 1, unregisterCallback.get() );
    }
}
