package com.opendxl.client;

/**
 * The disconnect strategy interface allows for specifying how disconnections from the broker should be handled.
 */
public interface DisconnectedStrategy {

    /**
     * Invoked when the client is disconnected from the broker.
     *
     * @param client The DXL client
     */
    void onDisconnect(DxlClient client);
}