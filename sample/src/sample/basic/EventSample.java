package sample.basic;

import com.opendxl.client.DxlClient;
import com.opendxl.client.DxlClientConfig;
import com.opendxl.client.callback.EventCallback;
import com.opendxl.client.message.Event;
import com.opendxl.client.message.Message;
import sample.Common;

import java.io.UnsupportedEncodingException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This sample demonstrates how to register a callback to receive {@link Event messages} from the DXL fabric.
 * Once the callback is registered, the sample sends a set number of {@link Event} messages to the fabric and waits
 * for them all to be received by the callback.
 */
public class EventSample {
    /** The topic to publish to */
    private static final String EVENT_TOPIC = "/isecg/sample/basicevent";
    /** The total number of events to send */
    private static final int TOTAL_EVENTS = 1000;
    /** Lock used to protect changes to counter */
    private static final Lock eventCountLock = new ReentrantLock();
    /** Condition used to protect changes to counter */
    private static final Condition eventCountCondition = eventCountLock.newCondition();
    /** The count of events that have been received */
    private static int eventCount = 0;

    /** Private constructor */
    private EventSample() {
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
            // Register callback and subscribe
            //

            // Create and add event listener
            final EventCallback myEventCallback =
                event -> {
                    try {
                        System.out.println("Received event: "
                            + new String(event.getPayload(), Message.CHARSET_UTF8));
                    } catch (UnsupportedEncodingException ex) {
                        ex.printStackTrace();
                    }

                    eventCountLock.lock();
                    try {
                        eventCount++;
                        eventCountCondition.signalAll();

                    } finally {
                        eventCountLock.unlock();
                    }
                };
            client.addEventCallback(EVENT_TOPIC, myEventCallback);

            //
            // Send events
            //

            // Record the start time
            final long startTime = System.currentTimeMillis();

            // Loop and send the events
            for (int count = 0; count < TOTAL_EVENTS; count++) {
                final Event event = new Event(EVENT_TOPIC);
                event.setPayload(Integer.toString(count).getBytes(Message.CHARSET_UTF8));
                client.sendEvent(event);
            }

            // Wait until all events have been received
            System.out.println("Waiting for events to be received...");
            eventCountLock.lock();
            try {
                while (eventCount < TOTAL_EVENTS) {
                    eventCountCondition.await();
                }
            } finally {
                eventCountLock.unlock();
            }

            // Print the elapsed time
            System.out.println("Elapsed time (ms): " + (System.currentTimeMillis() - startTime));
        }
    }
}
