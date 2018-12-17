/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client;

import com.opendxl.client.DxlClient;
import com.opendxl.client.callback.RequestCallback;
import com.opendxl.client.message.ErrorResponse;
import com.opendxl.client.message.Request;
import com.opendxl.client.message.Response;

import java.util.concurrent.ExecutorService;

/**
 * Simple test service that sends back a generic {@link Response} or {@link ErrorResponse}
 */
public class TestService implements RequestCallback, AutoCloseable {

    /**
     * The client
     */
    private DxlClient client;

    /**
     * Whether to return standard responses or errors
     */
    private boolean returnError = false;

    /**
     * Thread pool
     */
    private ExecutorService executor = null;

    /**
     * The error code to return
     */
    private int errorCode = 99;

    /**
     * The error message to return
     */
    private String errorMessage = "Error";

    /**
     * Constructs the test service
     *
     * @param client The client
     */
    public TestService(final DxlClient client) {
        this(client, 1);
    }

    /**
     * Constructs the test service
     *
     * @param client The client
     * @param threadCount The count of threads for the pool
     */
    private TestService(final DxlClient client, final int threadCount) {
        this.client = client;
        this.executor = Executors.createDaemonExecutor(threadCount, threadCount);
    }

    /**
     * Whether to return standard {@link Response} messages or {@link ErrorResponse} messages.
     *
     * @param val Whether to return standard {@link Response} messages or {@link ErrorResponse} messages.
     */
    public void setReturnError(final boolean val) {
        this.returnError = val;
    }

    /**
     * Sets the error code to return
     *
     * @param code The error code to return
     */
    public void setErrorCode(final int code) {
        this.errorCode = code;
    }

    /**
     * Sets the error message to return
     *
     * @param message The error message
     */
    public void setErrorMessage(final String message) {
        this.errorMessage = message;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onRequest(final Request request) {
        // Execute response via thread pool
        this.executor.execute(
            () -> {
                try {
                    this.client.sendResponse(
                        this.returnError
                            ? new ErrorResponse(this.client, request, this.errorCode, this.errorMessage)
                            : new Response(this.client, request));
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        this.executor.shutdownNow();
    }
}