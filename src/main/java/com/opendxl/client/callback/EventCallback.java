/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client.callback;

import com.opendxl.client.message.Event;

/**
 * Callback interface to receive {@link Event} messages.
 */
public interface EventCallback extends MessageCallback {
    /**
     * Invoked when an {@link Event} has been received.
     *
     * @param event The event
     */
    void onEvent(Event event);
}
