/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client;

/**
 * Runners allows large test cases to be broken out into individual classes, but still be part of a larger suite of
 * tests that can be inherited via a common base class. The {@link DxlClientTest} contains a suite of tests
 * for concrete {@link DxlClient} implementations. Many of the tests are broken into runner implementations.
 *
 * @see DxlClientTest
 * @see SyncRequestRunner
 * @see AsyncRequestRunner
 * @see EventsRunner
 * @see MessagePayloadRunner
 * @see SyncRequestThroughputRunner
 */
public abstract class AbstractRunner extends AbstractDxlTest {
    /**
     * Runs a test
     *
     * @param clientFactory Factory used to create {@link DxlClient}
     *                      instances
     */
    public abstract void runTest(DxlClientFactory clientFactory) throws Exception;
}
