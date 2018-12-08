/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client.callback;

import com.opendxl.client.message.Request;

/**
 * Callback interface to receive {@link Request} messages.
 */
public interface RequestCallback extends MessageCallback {
    /**
     * Invoked when a {@link Request} has been received.
     *
     * @param request The request
     */
    void onRequest(Request request);
}
