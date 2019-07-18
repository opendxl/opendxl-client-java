package com.opendxl.client;

import com.opendxl.client.exception.DxlException;
import com.opendxl.client.testutil.impl.DxlClientImplFactory;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Test class for verifying WebSocket connection to a broker via a proxy
 */
public class ProxyUsageVerificationTest {

    /**
     * Test to verifying WebSocket connection to a broker via a proxy
     *
     * @throws DxlException If there is an issue getting the DXL Client config
     */
    @Test
    public void verifyProxyUsage() throws DxlException {

        DxlClientImplFactory dxlClientImplFactory = DxlClientImplFactory.getDefaultInstance();
        DxlClientConfig config = dxlClientImplFactory.getConfig();

        int originalProxyPort = config.getProxyPort();
        // Modify the proxy port to be something other than the proxy port
        config.setProxyPort(originalProxyPort - 10);

        // Attempt to connect with the invalid proxy information and expect a connection refused failure
        try (DxlClient client = new DxlClient(config)) {
            client.getConfig().setConnectRetries(1);
            client.connect();
        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(e.getMessage().contains("Unable to connect to server: Connection refused"));
        }

        // Attempt to connect with the valid proxy information and do not expect an exception
        config.setProxyPort(originalProxyPort);
        try (DxlClient client = new DxlClient(config)) {
            client.getConfig().setConnectRetries(1);
            client.connect();
        } catch (Exception e) {
            fail();
        }
    }
}
