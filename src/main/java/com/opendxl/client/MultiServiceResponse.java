package com.opendxl.client;

import com.opendxl.client.message.ErrorResponse;
import com.opendxl.client.message.Request;
import com.opendxl.client.message.Response;

import java.util.Collections;
import java.util.Set;

/**
 * A response that is received as a result of a multi-service request.
 *
 * @see DxlClient#syncMultiServiceRequest(Request)
 * @see DxlClient#syncMultiServiceRequest(Request, long)}
 */
public class MultiServiceResponse {
    /** The initial response received from the broker */
    private Response initialResponse;
    /** The responses received from the services that were invoked as part of the multi-service request */
    @SuppressWarnings("unchecked")
    private Set<Response> responses = Collections.EMPTY_SET;
    /** Whether the multi-service request was "fully" successful */
    private boolean success = false;
    /** The count of expected responses */
    private int expectedCount;

    /**
     * Constructs the response
     *
     * @param initialResponse The initial response received from the broker
     */
    MultiServiceResponse(final Response initialResponse) {
        this.initialResponse = initialResponse;
    }

    /**
     * Constructs the response
     *
     * @param initialResponse The initial response received from the broker
     * @param expectedCount The count of expected responses
     * @param receivedResponses The responses received from the services that were invoked as
     *                          part of the multi-service request
     */
    MultiServiceResponse(final Response initialResponse,
                         final int expectedCount,
                         final Set<Response> receivedResponses) {
        this(initialResponse);
        this.expectedCount = expectedCount;
        this.responses = receivedResponses;

        if (this.expectedCount == this.responses.size()) {
            boolean error = false;
            for (Response res : this.responses) {
                if (res instanceof ErrorResponse) {
                    error = true;
                    break;
                }
            }
            if (!error) {
                this.success = true;
            }
        }
    }

    /**
     * The initial response received from the broker (contains information
     * regarding the services that are being invoked as part of the multi-
     * service request).
     *
     * @return  The initial response received from the broker
     */
    public Response getInitialResponse() {
        return this.initialResponse;
    }

    /**
     * Whether the multi-service request was "fully" successful. "Fully" successful
     * is defined as having all responses received from the invoked services.
     * Additionally, all responses received must be of a non-error type (not instances
     * of {@link ErrorResponse}).
     *
     * @return  Whether the multi-service request was "fully" successful.
     */
    public boolean isSuccess() {
        return this.success;
    }

    /**
     * Returns the count of received responses
     *
     * @return The count of received responses
     */
    public int getReceivedResponseCount() {
        return this.responses.size();
    }

    /**
     * Returns the expected response count
     *
     * @return The expected response count
     */
    public int getExpectedResponseCount() {
        return this.expectedCount;
    }

    /**
     * The responses received as a result of the multi-service request
     *
     * @return The responses received as a result of the multi-service request
     */
    public Set<Response> getResponses() {
        return this.responses;
    }
}