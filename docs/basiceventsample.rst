Basic Event Sample
==================

This sample demonstrates how to register a callback to receive
`Event <javadoc/index.html?com/opendxl/client/message/Event.html>`_  messages
from the DXL fabric. Once the callback is registered, the sample sends a set number of
`Event <javadoc/index.html?com/opendxl/client/message/Event.html>`_ messages to the fabric and waits for them all to
be received by the callback.

Prior to running this sample make sure you have completed the samples configuration step (:doc:`sampleconfig`).

To run this sample execute the ``sample\runsample`` script as follows:

    .. parsed-literal::

        c:\\dxlclient-java-sdk-\ |version|\>sample\\runsample sample.basic.EventSample

The output should appear similar to the following:

    .. code-block:: python

        Received event: 0
        Received event: 1
        Received event: 2
        Received event: 3
        Received event: 4
        Received event: 5

        ...

        Received event: 994
        Received event: 995
        Received event: 996
        Received event: 997
        Received event: 998
        Received event: 999
        Elapsed time (ms): 441.999912262

The code for the sample is broken into two main sections.

The first section is responsible for registering an
`EventCallback <javadoc/index.html?com/opendxl/client/callback/EventCallback.html>`_ for a specific topic. The
`DxlClient.addEventCallback() <javadoc/com/opendxl/client/DxlClient.html#addEventCallback-java.lang.String-com.opendxl.client.callback.EventCallback->`_ method
registers the callback and subscribes to the topic.

    .. code-block:: java

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
    
The second section sends a set amount of `Event <javadoc/index.html?com/opendxl/client/message/Event.html>`_ messages via the
`DxlClient.sendEvent <javadoc/com/opendxl/client/DxlClient.html#sendEvent-com.opendxl.client.message.Event->`_ method
of the `DxlClient <javadoc/index.html?com/opendxl/client/DxlClient.html>`_.

It then waits for all of the events to be received by the
`EventCallback <javadoc/index.html?com/opendxl/client/callback/EventCallback.html>`_ that was previously registered.

    .. code-block:: java

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
