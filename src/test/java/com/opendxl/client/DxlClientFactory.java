/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client;

import com.opendxl.client.exception.DxlException;

/**
 * Factory for creating {@link DxlClient} instances
 */
public interface DxlClientFactory {
    /**
     * Returns the DXL client configuration
     *
     * @return The DXL client configuration
     * @throws DxlException If an error occurs
     */
    DxlClientConfig getConfig() throws DxlException;

    /**
     * Creates and returns a new {@link DxlClient} instance
     *
     * @return A new {@link DxlClient} instances
     * @throws DxlException If a DXL exception occurs
     */
    DxlClient newClientInstance() throws DxlException;
}
