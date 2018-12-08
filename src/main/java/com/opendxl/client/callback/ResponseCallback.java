/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client.callback;

import com.opendxl.client.message.Response;

/**
 * Callback interface to receive {@link Response} messages.
 */
public interface ResponseCallback extends MessageCallback {
    /**
     * Invoked when a {@link Response} has been received.
     *
     * @param response The response
     */
    void onResponse(Response response);
}
