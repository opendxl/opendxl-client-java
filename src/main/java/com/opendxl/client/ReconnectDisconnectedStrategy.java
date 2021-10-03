/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * An implementation of the {@link DisconnectedStrategy} that attempts to reconnect to the fabric
 */
public class ReconnectDisconnectedStrategy implements DisconnectedStrategy {
    /**
     * The logger
     */
    private static Logger logger = LogManager.getLogger(ReconnectDisconnectedStrategy.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDisconnect(final DxlClient client) {
        try {
            client.reconnect();
        } catch (Exception ex) {
            logger.error("Unable to reconnect", ex);
        }
    }
}
