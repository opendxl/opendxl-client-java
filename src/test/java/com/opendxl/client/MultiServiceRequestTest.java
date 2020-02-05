package com.opendxl.client;


import com.fasterxml.jackson.core.type.TypeReference;
import com.opendxl.client.callback.RequestCallback;
import com.opendxl.client.exception.DxlException;
import com.opendxl.client.message.ErrorResponse;
import com.opendxl.client.message.Message;
import com.opendxl.client.message.Request;
import com.opendxl.client.message.Response;
import com.opendxl.client.testutil.impl.DxlClientImplFactory;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MultiServiceRequestTest extends AbstractDxlTest {
    /**
     * The client
     */
    DxlClient client = null;
    /**
     * The topic for the service to respond to
     */
    private static final String SERVICE_TOPIC = "/opendxl/sample/basicservice";
    /**
     * The amount of time to wait for operations to complete
     */
    private static final long TIMEOUT = 10 * 1000; /* 10 seconds */

    /** value to use to not respond to a request to simulate partial responses */
    private static final AtomicInteger RESPONSE_COUNT = new AtomicInteger();

    /**
     * {@inheritDoc}
     */
    @Before
    public void beforeTest() throws Exception {
        super.beforeTest();

        client = DxlClientImplFactory.getDefaultInstance().newClientInstance();
        client.connect();
    }

    /**
     * {@inheritDoc}
     */
    @After
    public void afterTest() throws Exception {
        try {
            client.close();
            RESPONSE_COUNT.set(0);
        } finally {
            client = null;
        }
    }

    @Test
    public void testMultiServiceRequestNoService() throws Exception {
        // Create the request message
        Request req = new Request(SERVICE_TOPIC + "/invalid");
        // Populate the request payload
        req.setPayload("ping".getBytes(Message.CHARSET_UTF8));
        MultiServiceResponse response = client.syncMultiServiceRequest(req, 5 * 1000);
        //no service so should be an error response and not successful
        assertTrue(response.getInitialResponse() instanceof ErrorResponse);
        assertFalse(response.isSuccess());
        assertTrue(response.getExpectedResponseCount() == 0);
        assertTrue(response.getReceivedResponseCount() == 0);
        assertTrue(response.getResponses().isEmpty());
        assertTrue(response.getInitialResponse().getRequestMessageId().equals(req.getMessageId()));

        //register different topic ( should not affect result)
        ServiceRegistrationInfo info = new ServiceRegistrationInfo(client, "Somethingelse");
        try {

            final RequestCallback myRequestCallback =
                    request -> {
                        try {
                            System.out.println("Service received request payload: "
                                    + new String(request.getPayload(), Message.CHARSET_UTF8));

                            System.out.println("Multi-service request: " + request.isMultiServiceRequest());

                            final Response res = new Response(request);
                            res.setPayload("testing".getBytes(Message.CHARSET_UTF8));

                            client.sendResponse(res);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    };

            // Add a topic for the service to respond to
            info.addTopic(SERVICE_TOPIC + "Foo", myRequestCallback);

            // Register the service with the fabric (wait up to 10 seconds for registration to complete)
            client.registerServiceSync(info, TIMEOUT);

            response = client.syncMultiServiceRequest(req, 5 * 1000);
            //no service so should be an error response and not successful
            assertTrue(response.getInitialResponse() instanceof ErrorResponse);
            assertFalse(response.isSuccess());
            assertTrue(response.getExpectedResponseCount() == 0);
            assertTrue(response.getReceivedResponseCount() == 0);
            assertTrue(response.getResponses().isEmpty());
            assertTrue(response.getInitialResponse().getRequestMessageId().equals(req.getMessageId()));

        } finally {
            client.unregisterServiceAsync(info.getServiceId());
        }
    }

    @Test
    public void testMultiServiceRequestAndResponse() throws Exception {

        Set<String> serviceIds = new HashSet<>();
        String responseString = "pong";
        try {
            int numServices = 6;
            int numInstancesPerService = 1;
            serviceIds = setUpServices(numServices, numInstancesPerService, responseString, false);

            // Create the request message
            Request req = new Request(SERVICE_TOPIC);
            // Populate the request payload
            req.setPayload("ping".getBytes(Message.CHARSET_UTF8));
            final MultiServiceResponse response = client.syncMultiServiceRequest(req, 5 * 1000);
            String initialResponse = new String(response.getInitialResponse().getPayload(), Message.CHARSET_UTF8);
            System.out.println(initialResponse);
            /**
             * Response structure has private access
             *
             * {"requests":{
             * "{a7bd0e0b-cee1-4237-bcd4-143a969f3b5e}:1250125d-84f5-4009-b9c1-6da67a0b4638":{"serviceGuid":"{6197ec01-4baf-44d8-9583-a2007f2d12a9}"},
             * "{a7bd0e0b-cee1-4237-bcd4-143a969f3b5e}:19ac27a2-6fdb-4937-9cff-d9d1fa2f4921":{"serviceGuid":"{8d156a7c-48bb-4170-ba8c-dc6e1561b5c5}"},
             * "{a7bd0e0b-cee1-4237-bcd4-143a969f3b5e}:36eb57b2-f234-4c91-8611-7e51b43b5aeb":{"serviceGuid":"{e3682762-40e1-45c0-bcb4-7fd3e28ff7bf}"},
             * "{a7bd0e0b-cee1-4237-bcd4-143a969f3b5e}:512a5a21-64cf-4899-975f-b57ffbbdd8d6":{"serviceGuid":"{7e763518-2ef1-4708-88ba-7021eb92b12b}"},
             * "{a7bd0e0b-cee1-4237-bcd4-143a969f3b5e}:79c3bb6e-92f7-4159-9fd8-ef602104e2d3":{"serviceGuid":"{fb80be31-9d82-4d6f-bcdd-fc7a4dc1bb28}"},
             * "{a7bd0e0b-cee1-4237-bcd4-143a969f3b5e}:e16b71ac-6437-4575-91b2-edf5f58bf687":{"serviceGuid":"{b8819862-4640-4fbd-b900-62c1c6dd6b5e}"}}}
             */
            final Map<String, Object> stringObjectMap = JsonUtils.fromString(initialResponse,
                    new TypeReference<Map<String, Object>>() {
                    });

            //get the service ids from the response they should match the ones in the ids from the registration
            Collection serviceGuidsFromResponse = stringObjectMap.values();
            boolean serviceIdInReponse = true;
            int count = 0;
            for (Object o : serviceGuidsFromResponse) {
                final Collection values = ((HashMap) o).values();

                for (Object value : values) {
                    boolean found = false;
                    for (String serviceId : serviceIds) {
                        if (((String) ((HashMap) value).get("serviceGuid")).contains(serviceId)) {
                            found = true;
                            count++;
                        }
                        if (found) {
                            break;
                        }
                    }
                    serviceIdInReponse = serviceIdInReponse & found;
                }
            }
            assertTrue(serviceIdInReponse);
            assertTrue(count == numServices); //one response per service
            assertTrue(response.getInitialResponse() instanceof Response);
            assertTrue(response.getInitialResponse().getRequestMessageId().equals(req.getMessageId()));
            assertTrue(response.getExpectedResponseCount() == numServices);
            assertTrue(response.getReceivedResponseCount() == numServices);
            assertTrue(response.isSuccess());
            assertTrue(response.getResponses().size() == numServices); //one response per service
            for (Response resp : response.getResponses()) {
                final String payload = new String(resp.getPayload(), Message.CHARSET_UTF8);
                assertTrue(StringUtils.equals(responseString, payload));
            }
        } finally {
            for (String serviceId : serviceIds) {
                client.unregisterServiceAsync(serviceId);
            }
        }
    }

    @Test
    public void testMultiServiceRequestPartialResponses() throws Exception {

        Set<String> serviceIds = new HashSet<>();
        String responseString = "pong";
        try {
            int numServices = 6;
            int numInstancesPerService = 1;
            boolean skipLastResponse = true;
            serviceIds = setUpServices(numServices, numInstancesPerService, responseString, skipLastResponse) ;

            // Create the request message
            Request req = new Request(SERVICE_TOPIC);
            // Populate the request payload
            req.setPayload("ping".getBytes(Message.CHARSET_UTF8));

            final MultiServiceResponse response = client.syncMultiServiceRequest(req, 5 * 1000);
            String initialResponse = new String(response.getInitialResponse().getPayload(), Message.CHARSET_UTF8);
            final Map<String, Object> stringObjectMap = JsonUtils.fromString(initialResponse,
                    new TypeReference<Map<String, Object>>() {
                    });

            assertTrue(response.getInitialResponse() instanceof Response);
            assertTrue(response.getInitialResponse().getRequestMessageId().equals(req.getMessageId()));
            assertTrue(response.getExpectedResponseCount() == numServices);
            assertTrue(response.getReceivedResponseCount() == numServices);
            assertFalse(response.isSuccess()); //failed
            assertTrue(response.getResponses().size() == numServices);

            boolean foundErrorResponse = false;
            for (Response resp : response.getResponses()) {
                if (!(resp instanceof ErrorResponse)) {
                    final String payload = new String(resp.getPayload(), Message.CHARSET_UTF8);
                    assertTrue(StringUtils.equals(responseString, payload));
                } else {
                    foundErrorResponse = true;
                }
            }
            assertTrue(foundErrorResponse);  //one should be error
        } finally {
            for (String serviceId : serviceIds) {
                client.unregisterServiceAsync(serviceId);
            }
        }
    }

    /**
     * Set up services and return the serviceIds to validate the initial response which contains the serviceguids
     * @param numServices  number of Services to register
     * @param numInstancesPerService number of instances per service sot register
     * @param returnErrorResponse
     * @return
     * @throws DxlException
     */
    private Set<String> setUpServices(final int numServices, final int numInstancesPerService,
                                      String responseString, final boolean returnErrorResponse) throws DxlException {
        // Create incoming request callback
        final RequestCallback myRequestCallback =
                request -> {
                    try {
                        int c = RESPONSE_COUNT.incrementAndGet();
                        Response res;
                        if (returnErrorResponse &&  c == numServices) {
                            System.out.println("Sending error response to test partial responses");
                            res = new ErrorResponse(request, 404, "just skipping");
                        } else {
                            res = new Response(request);
                            res.setPayload(responseString.getBytes(Message.CHARSET_UTF8));

                            System.out.println("Service received request payload: "
                                    + new String(request.getPayload(), Message.CHARSET_UTF8));
                            System.out.println("Multi-service request: " + request.isMultiServiceRequest());

                        }
                        client.sendResponse(res);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                };

        // Create service registration object
        ServiceRegistrationInfo info = null;
        Set<String> serviceGuids = new HashSet<>();

        for (int j = 0; j < numInstancesPerService; j++) {
            for (int i = 0; i < numServices; i++) {
                // Create service registration object
                info = new ServiceRegistrationInfo(client, "myService" + i);

                // Add a topic for the service to respond to
                info.addTopic(SERVICE_TOPIC, myRequestCallback);

                // Register the service with the fabric (wait up to 10 seconds for registration to complete)
                client.registerServiceSync(info, TIMEOUT);
                //System.out.println("!!!!!!!!!!!:" +info.getServiceId());
                serviceGuids.add(info.getServiceId());
            }
        }

        return serviceGuids;
    }
}
