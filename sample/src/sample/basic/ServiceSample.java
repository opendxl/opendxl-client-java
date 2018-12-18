package sample.basic;

import com.opendxl.client.DxlClient;
import com.opendxl.client.DxlClientConfig;
import com.opendxl.client.ServiceRegistrationInfo;
import com.opendxl.client.callback.RequestCallback;
import com.opendxl.client.message.ErrorResponse;
import com.opendxl.client.message.Message;
import com.opendxl.client.message.Request;
import com.opendxl.client.message.Response;
import sample.Common;

/**
 * This sample demonstrates how to register a DXL service to receive {@link Request} messages and send
 * {@link Response} messages back to an invoking client.
 */
public class ServiceSample {

    /** The topic for the service to respond to */
    private static final String SERVICE_TOPIC = "/isecg/sample/basicservice";
    /** The amount of time to wait for operations to complete */
    private static final long TIMEOUT = 10 * 1000; /* 10 seconds */

    /** Private constructor */
    private ServiceSample() {
        super();
    }

    /**
     * Main method
     *
     * @param args Aguments to main
     * @throws Exception If an error occurs
     */
    public static void main(String[] args) throws Exception {

        // Create DXL configuration from file
        final DxlClientConfig config = Common.getClientConfig(args);

        // Create the client
        try (DxlClient client = new DxlClient(config)) {

            // Connect to the fabric
            client.connect();

            //
            // Register the service
            //

            // Create incoming request callback
            final RequestCallback myRequestCallback =
                request -> {
                    try {
                        System.out.println("Service received request payload: "
                            + new String(request.getPayload(), Message.CHARSET_UTF8));

                        final Response res = new Response(request);
                        res.setPayload("pong".getBytes(Message.CHARSET_UTF8));

                        client.sendResponse(res);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                };

            // Create service registration object
            final ServiceRegistrationInfo info = new ServiceRegistrationInfo(client, "myService");

            // Add a topic for the service to respond to
            info.addTopic(SERVICE_TOPIC, myRequestCallback);

            // Register the service with the fabric (wait up to 10 seconds for registration to complete)
            client.registerServiceSync(info, TIMEOUT);

            //
            // Invoke the service (send a request)
            //

            try {
                // Create the request message
                final Request req = new Request(SERVICE_TOPIC);

                // Populate the request payload
                req.setPayload("ping".getBytes(Message.CHARSET_UTF8));

                // Send the request and wait for a response (synchronous)
                final Response res = client.syncRequest(req);

                // Extract information from the response (Check for errors)
                if (res.getMessageType() != Message.MESSAGE_TYPE_ERROR) {
                    System.out.println("Client received response payload: "
                        + new String(res.getPayload(), Message.CHARSET_UTF8));
                } else {
                    System.out.println("Error: " + ((ErrorResponse) res).getErrorMessage());
                }
            } finally {
                client.unregisterServiceSync(info, TIMEOUT);
            }
        }
    }
}
