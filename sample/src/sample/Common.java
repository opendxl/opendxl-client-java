package sample;

import com.opendxl.client.DxlClientConfig;
import com.opendxl.client.exception.DxlException;

/**
 * Common methods for the samples
 */
public class Common {

    /** Private constructor */
    private Common() {
        super();
    }

    /**
     * Returns the {@link DxlClientConfig} corresponding to the arguments specified on the command line.
     * {@code Argument 1} must be the location of the client configuration file.
     * @param args The command line arguments
     * @return The {@link DxlClientConfig} corresponding to the arguments specified on the command line.
     * @throws DxlException
     */
    public static DxlClientConfig getClientConfig(final String[] args) throws DxlException {
        if (args.length < 1) {
            throw new DxlException("A client configuration file must be specified.");
        }

        return DxlClientConfig.createDxlConfigFromFile(args[0]);
    }
}
