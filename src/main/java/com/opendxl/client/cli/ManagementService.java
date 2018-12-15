package com.opendxl.client.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContexts;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Collection;
import java.util.List;

/**
 * Helpers for making requests to a Management Service for CLI subcommands
 */
class ManagementService {

    /**
     * Colon delimiter use to delimit the response status and result of
     * invoking a command on the Management Service
     */
    private static final String COLON_DELIMITER = ":";

    /**
     * The OK response status from invoking a command on the Management Service
     */
    private static final String OK_STATUS = "OK";

    //
    // Connection management
    //
    /**
     * The default connection pool maximum total of connections
     */
    private static final int DEFAULT_CONN_POOL_MAX_TOTAL = 100;
    /**
     * The default connection pool maximum total of connections per route
     */
    private static final int DEFAULT_CONN_POOL_MAX_PER_ROUTE = 100;

    //
    // Timeouts
    //
    /**
     * The default initial connect timeout
     */
    private static final int DEFAULT_CONNECT_TIMEOUT = 5000;
    /**
     * The default connection request timeout
     */
    private static final int DEFAULT_CONNECT_REQUEST_TIMEOUT = 5000;
    /**
     * The default socket timeout
     */
    private static final int DEFAULT_SOCKET_TIMEOUT = 5000;

    //
    // TLS
    //
    /**
     * The supported TLS protocols to use
     */
    private static final String[] SUPPORTED_PROTOCOLS = new String[] {"TLSv1", "TLSv1.1", "TLSv1.2"};

    /**
     * Management Service host (FQDN or IP address)
     */
    private String host;
    /**
     * Management Service port
     */
    private int port;
    /**
     * The user name to authenticating with the Management Service
     */
    private String userName;
    /**
     * The password to authenticating with the Management Service
     */
    private String password;
    /**
     * The location of the file containing certificates used to do HTTPS certificate validation
     */
    private String trustStoreFile;
    /**
     * The base URL for the Management Service
     */
    private String baseUrl;
    /**
     * The http client for making a request to the Management Service
     */
    private CloseableHttpClient httpClient;

    /**
     * Constructor for the Management Service
     *
     * @param host           Management Service host (FQDN or IP address)
     * @param port           Management Service port
     * @param userName       The user name for authenticating with the Management Service
     * @param password       The password for authenticating with the Management Services
     * @param trustStoreFile The location of the file containing certificates used to HTTPS certificate validation
     * @throws CertificateException     If there is an error creating certificates from the certificates in the
     *                                  trust store file
     * @throws NoSuchAlgorithmException If there is an issue creating a certificate trust manager
     * @throws KeyStoreException        If there is an error creating a key store from the certificates in the
     *                                  trust store file
     * @throws KeyManagementException   If there is an error creating a key store from the certificates in the
     *                                  trust store file
     * @throws IOException              If there is an error creating a key store from the certificates in the
     *                                  trust store file
     */
    ManagementService(String host, int port, String userName, String password, String trustStoreFile)
            throws CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException,
            IOException {
        this.host = host;
        this.port = port;
        this.userName = userName;
        this.password = password;
        this.trustStoreFile = trustStoreFile;
        this.baseUrl = "https://" + this.host + ":" + this.port + "/remote";

        // Create the http client
        this.httpClient = createTlsHttpClient();
    }

    /**
     * Creates and returns a new HTTP client (TLS)
     *
     * @return A newly created HTTP client (TLS)
     */
    private CloseableHttpClient createTlsHttpClient() throws NoSuchAlgorithmException, CertificateException,
            KeyStoreException, IOException, KeyManagementException {
        TrustManager[] tm;

        // Create a trust manager with the certificate chain
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());

        // By default host name validation is false
        boolean hostNameValidation = false;

        if (StringUtils.isNotBlank(this.trustStoreFile)) {
            tmf.init(createKeystore(this.trustStoreFile));
            tm = tmf.getTrustManagers();
            hostNameValidation = true;
        } else {
            tm = new TrustManager[] {
                    new X509TrustManager() {
                        public void checkClientTrusted(
                                final X509Certificate[] chain, final String authType) throws CertificateException {
                        }

                        public void checkServerTrusted(
                                final X509Certificate[] chain, final String authType) throws CertificateException {
                        }

                        public X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }
                    }
            };
        }

        // Trust certs signed by the provided CA chain
        final SSLContext sslcontext = SSLContexts.createDefault();
        sslcontext.init(null, tm, null);

        SSLConnectionSocketFactory socketFactory;
        if (hostNameValidation) {
            // Allow TLSv1 protocol only
            socketFactory =
                    new SSLConnectionSocketFactory(
                            sslcontext, SUPPORTED_PROTOCOLS, null, new DefaultHostnameVerifier());
        } else {
            // Disable hostname verification
            socketFactory =
                    new SSLConnectionSocketFactory(
                            sslcontext, SUPPORTED_PROTOCOLS, null,
                            (hostName, session) -> true
                    );
        }

        return HttpClients.custom()
                .setDefaultRequestConfig(createRequestConfig())
                .setConnectionManager(createConnectionManager(socketFactory))
                .setDefaultCookieStore(new BasicCookieStore())
                .setRedirectStrategy(new LaxRedirectStrategy())
                .build();
    }

    /**
     * Creates a KeyStore for use with the HttpClient
     *
     * @param caChainPems the pems to be added in a single string format
     * @return The KeyStore
     * @throws KeyStoreException        If there is an error creating a key store from the certificates in the
     *                                  trust store file
     * @throws CertificateException     If there is an error creating a certificatefrom the certificates in the
     *                                  trust store file
     * @throws NoSuchAlgorithmException If there is an error creating a certificate factory
     * @throws IOException              If there is an error creating a key store from the certificates in
     *                                  the trust store file
     */
    private KeyStore createKeystore(String caChainPems) throws KeyStoreException, CertificateException,
            NoSuchAlgorithmException, IOException {
        final KeyStore clientKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        clientKeyStore.load(null, null);

        final CertificateFactory certFactory = CertificateFactory.getInstance("X.509");

        final Collection<? extends Certificate> chain =
                certFactory.generateCertificates(
                        new ByteArrayInputStream(caChainPems.getBytes()));

        int certNumber = 0;
        for (Certificate caCert : chain) {
            clientKeyStore.setCertificateEntry(Integer.toString(certNumber), caCert);
            certNumber++;
        }

        return clientKeyStore;
    }

    /**
     * Creates and returns a {@link RequestConfig}
     *
     * @return A {@link RequestConfig} based on the specified descriptor properties
     */
    private RequestConfig createRequestConfig() {
        final RequestConfig.Builder builder = RequestConfig.custom();
        builder.setConnectTimeout(DEFAULT_CONNECT_TIMEOUT);
        builder.setConnectionRequestTimeout(DEFAULT_CONNECT_REQUEST_TIMEOUT);
        builder.setSocketTimeout(DEFAULT_SOCKET_TIMEOUT);
        builder.setCookieSpec(CookieSpecs.STANDARD);

        return builder.build();
    }

    /**
     * Creates and returns a {@link HttpClientConnectionManager}
     *
     * @param socketFactory The socket factory for TLS. pass null for non-TLS
     * @return A {@link HttpClientConnectionManager} based on the specified descriptor properties
     */
    private HttpClientConnectionManager createConnectionManager(SSLConnectionSocketFactory socketFactory) {
        final PoolingHttpClientConnectionManager cm;
        if (socketFactory != null) {
            Registry<ConnectionSocketFactory> socketFactoryRegistry =
                    RegistryBuilder.<ConnectionSocketFactory>create()
                            .register("https", socketFactory)
                            .register("http", PlainConnectionSocketFactory.INSTANCE)
                            .build();
            cm = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
        } else {
            cm = new PoolingHttpClientConnectionManager();
        }
        cm.setMaxTotal(DEFAULT_CONN_POOL_MAX_TOTAL); // TODO change these values to be lower????
        cm.setDefaultMaxPerRoute(DEFAULT_CONN_POOL_MAX_PER_ROUTE);
        return cm;
    }

    /**
     * Invoke the input command name on the Management Service via an HTTPS request and return the response
     * as an instance of the specified return type class.
     *
     * @param commandName     The command name to be invoked on the Management Service
     * @param parameters      A list of parameters to pass to the command to be invoked on the Management Services
     * @param returnTypeClass The type class of the object to be returned by this method.
     * @param <T>  The type of the object to return from the invoked command
     * @return The response from invoking the command on the Management Service as an instance of the specified
     * return type class.
     * @throws URISyntaxException If there is an issue creating the Management Service URL with the input command name
     * @throws IOException        If the response from invoked command is invalid
     */
    <T> T invokeCommand(String commandName, List<NameValuePair> parameters, Class<T> returnTypeClass)
            throws URISyntaxException,
            IOException {
        HttpGet request = new HttpGet(this.baseUrl + "/" + commandName);
        URIBuilder uriBuilder = new URIBuilder(request.getURI());
        if (parameters != null && !parameters.isEmpty()) {
            uriBuilder.addParameters(parameters);
        }
        URI uri = uriBuilder.build();
        request.setURI(uri);

        String credentials = Base64.getEncoder().encodeToString(
                (this.userName + ":" + this.password).getBytes(StandardCharsets.UTF_8));
        request.setHeader(HttpHeaders.AUTHORIZATION, "Basic " + credentials);

        // Send the request
        try (CloseableHttpResponse response = this.httpClient.execute(request)) {
            final int httpStatus = response.getStatusLine().getStatusCode();
            if (httpStatus < 200 || httpStatus >= 300) {
                throw new IOException("HTTP Request failed: " + response.getStatusLine().toString());
            }

            String httpResponseBody = new BasicResponseHandler().handleResponse(response);

            final int colonDelimiterLocation = httpResponseBody.indexOf(COLON_DELIMITER);
            if (colonDelimiterLocation == -1) {
                throw new IOException("Did not find ':' status delimiter in response body");
            }
            final String status = httpResponseBody.substring(0, colonDelimiterLocation);
            final String responseDetail = httpResponseBody.substring(colonDelimiterLocation + 1).trim();

            if (!OK_STATUS.equals(status)) {
                throw new IOException(String.format("Request to %s failed with status: %d. Message: %s",
                        this.baseUrl + "/" + commandName, httpStatus, responseDetail));
            }

            return new ObjectMapper().readValue(responseDetail, returnTypeClass);
        }
    }
}
