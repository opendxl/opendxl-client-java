/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client.callback;

import com.opendxl.client.message.Response;

/**
 * Concrete instances of this interface are used to receive {@link Response} messages.
 * <P>
 * Response callbacks are typically used when invoking a service asynchronously.
 * </P>
 * <P>
 * The following is a simple example of using a response callback with an asynchronous service invocation:
 * </P>
 * <PRE>
 * ResponseCallback myResponseCallback =
 *     response -&gt; System.out.println("Received response! " + response.getServiceId());
 *
 * Request req = new Request("/testservice/testrequesttopic");
 * client.asyncRequest(req, myResponseCallback);
 * </PRE>
 */
public interface ResponseCallback extends MessageCallback {
    /**
     * Invoked when a {@link Response} has been received.
     *
     * @param response The {@link Response} message that was received
     */
    void onResponse(Response response);
}
