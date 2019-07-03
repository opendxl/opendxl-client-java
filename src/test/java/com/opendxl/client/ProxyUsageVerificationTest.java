package com.opendxl.client;

import com.opendxl.client.exception.DxlException;
import com.opendxl.client.testutil.impl.DxlClientImplFactory;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ProxyUsageVerificationTest {

    @Test
    public void verifyProxyUsage() throws DxlException {

        DxlClientImplFactory dxlClientImplFactory = DxlClientImplFactory.getDefaultInstance();
        DxlClientConfig config = dxlClientImplFactory.getConfig();

        int originalProxyPort = config.getProxyPort();

        config.setProxyPort(originalProxyPort - 10);
        try (DxlClient client = new DxlClient(config)) {
            client.getConfig().setConnectRetries(1);
            client.connect();
        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(e.getMessage().contains("Unable to connect to server: Connection refused: connect"));
        }

        config.setProxyPort(originalProxyPort);
        try (DxlClient client = new DxlClient(config)) {
            client.getConfig().setConnectRetries(1);
            client.connect();
        } catch (Exception e) {
            fail();
        }
    }
}
