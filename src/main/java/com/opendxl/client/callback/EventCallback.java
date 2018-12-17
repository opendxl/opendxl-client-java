/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client.callback;

import com.opendxl.client.DxlClient;
import com.opendxl.client.message.Event;

/**
 * Concrete instances of this interface are used to receive {@link Event} messages.
 * <P>
 * To receive events, a concrete instance of this callback must be created and registered with a {@link DxlClient}
 * instance via the {@link DxlClient#addEventCallback} method.
 * </P>
 * <P>
 * The following is a simple example of using an event callback:
 * </P>
 * <PRE>
 * final EventCallback myEventCallback =
 *     event -&gt; {
 *         try {
 *             System.out.println("Received event: "
 *                 + new String(event.getPayload(), Message.CHARSET_UTF8));
 *         } catch (UnsupportedEncodingException ex) {
 *             ex.printStackTrace();
 *         }
 *     };
 * client.addEventCallback("/testeventtopic", myEventCallback);
 * </PRE>
 * <P>
 * <B>NOTE</B>: By default when registering an event callback the client will automatically subscribe
 * ({@link DxlClient#subscribe}) to the topic.
 * </P>
 * <P>
 * The following demonstrates a client that is sending an event message that would be received by the callback above.
 * </P>
 * <PRE>
 * // Create the event message
 * Event event = new Event("/testeventtopic");
 *
 * // Populate the event payload
 * event.setPayload(Integer.toString(count).getBytes(Message.CHARSET_UTF8));
 *
 * // Send the event
 * client.sendEvent(event);
 * </PRE>
 */
public interface EventCallback extends MessageCallback {
    /**
     * Invoked when an {@link Event} has been received.
     *
     * @param event The {@link Event} message that was received
     */
    void onEvent(Event event);
}
