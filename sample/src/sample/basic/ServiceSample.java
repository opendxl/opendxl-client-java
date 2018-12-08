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

public class ServiceSample {

    private static final String SERVICE_TOPIC = "/isecg/sample/basicservice";
    private static final long TIMEOUT = 10 * 1000; /* 10 seconds */

    private ServiceSample() {
        super();
    }

    public static void main(String[] args) throws Exception {
        final DxlClientConfig config = Common.getClientConfig(args);
        try (DxlClient client = new DxlClient(config)) {

            client.connect();

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

            final ServiceRegistrationInfo info = new ServiceRegistrationInfo(client, "myService");
            info.addChannel(SERVICE_TOPIC, myRequestCallback);
            client.registerServiceSync(info, TIMEOUT);

            try {
                final Request req = new Request(SERVICE_TOPIC);
                req.setPayload("ping".getBytes(Message.CHARSET_UTF8));

                final Response res = client.syncRequest(req);

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
