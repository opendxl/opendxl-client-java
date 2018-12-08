package sample;

import com.opendxl.client.DxlClientConfig;
import com.opendxl.client.exception.DxlException;

public class Common {

    private Common() {
        super();
    }

    public static DxlClientConfig getClientConfig(final String[] args) throws DxlException {
        if (args.length < 1) {
            throw new DxlException("A client configuration file must be specified.");
        }

        return DxlClientConfig.createDxlConfigFromFile(args[0]);
    }
}
