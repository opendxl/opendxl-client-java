/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client.callback;

import com.opendxl.client.ServiceRegistrationInfo;
import com.opendxl.client.message.Request;

/**
 * Concrete instances of this interface are used to receive {@link Request} messages.
 * <P>
 * Request callbacks are typically used when implementing a "service".
 * </P>
 * <P>
 * See {@link ServiceRegistrationInfo} for more information on how to register a service.
 * </P>
 */
public interface RequestCallback extends MessageCallback {
    /**
     * Invoked when a {@link Request} has been received.
     *
     * @param request The {@link Request} message that was received
     */
    void onRequest(Request request);
}
