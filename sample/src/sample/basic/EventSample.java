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

public class EventSample {
    private static final String EVENT_TOPIC = "/isecg/sample/basicevent";
    private static final int TOTAL_EVENTS = 1000;
    private static final Lock eventCountLock = new ReentrantLock();
    private static final Condition eventCountCondition = eventCountLock.newCondition();
    private static int eventCount = 0;

    private EventSample() {
        super();
    }

    public static void main(String[] args) throws Exception {
        final DxlClientConfig config = Common.getClientConfig(args);
        try (DxlClient client = new DxlClient(config)) {

            client.connect();

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

            final long startTime = System.currentTimeMillis();

            for (int count = 0; count < TOTAL_EVENTS; count++) {
                final Event event = new Event(EVENT_TOPIC);
                event.setPayload(Integer.toString(count).getBytes(Message.CHARSET_UTF8));
                client.sendEvent(event);
            }

            System.out.println("Waiting for events to be received...");
            eventCountLock.lock();
            try {
                while (eventCount < TOTAL_EVENTS) {
                    eventCountCondition.await();
                }
            } finally {
                eventCountLock.unlock();
            }

            System.out.println("Elapsed time (ms): " + (System.currentTimeMillis() - startTime));
        }
    }
}
