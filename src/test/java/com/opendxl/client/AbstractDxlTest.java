/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client;

import com.opendxl.client.message.Message;
import org.junit.Before;

import static org.junit.Assert.assertEquals;

/**
 * Base class for DXL test cases
 */
public abstract class AbstractDxlTest {

    /**
     * Default timeout
     */
    public static final long DEFAULT_TIMEOUT = 5 * 60 * 1000;

    /**
     * Invoked prior to running test
     */
    @Before
    public void beforeTest() throws Exception {
    }

    /**
     * Asserts that the message is a response message
     *
     * @param message The message
     */
    void assertIsResponse(final Message message) {
        assertEquals(Message.MESSAGE_TYPE_RESPONSE, message.getMessageType());
    }

    /**
     * Asserts that the message is a error message
     *
     * @param message The message
     */
    void assertIsError(final Message message) {
        assertEquals(Message.MESSAGE_TYPE_ERROR, message.getMessageType());
    }
}
