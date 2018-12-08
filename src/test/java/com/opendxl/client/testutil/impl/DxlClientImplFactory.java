/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client.testutil.impl;

import com.opendxl.client.DxlClient;
import com.opendxl.client.DxlClientFactory;
import com.opendxl.client.DxlClientConfig;
import com.opendxl.client.exception.DxlException;

/**
 * Default {@link DxlClientFactory} for {@link DxlClient} clients.
 */
public class DxlClientImplFactory implements DxlClientFactory {
    /**
     * The client factory
     */
    private static DxlClientImplFactory clientFactory = new DxlClientImplFactory();

    /**
     * {@inheritDoc}
     */
    @Override
    public DxlClientConfig getConfig() throws DxlException {
        final String clientConfigFile = System.getProperty("clientConfig");
        return DxlClientConfig.createDxlConfigFromFile(clientConfigFile);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DxlClient newClientInstance() throws DxlException {
        return new DxlClient(getConfig());
    }

    /**
     * Returns the default {@link DxlClientImplFactory}
     *
     * @return The default {@link DxlClientImplFactory}
     */
    public static DxlClientImplFactory getDefaultInstance() {
        return clientFactory;
    }
}
