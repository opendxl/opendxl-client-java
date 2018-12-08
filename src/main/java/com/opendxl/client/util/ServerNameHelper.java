/*---------------------------------------------------------------------------*
 * Copyright (c) 2018 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.client.util;

import java.util.regex.Pattern;

public class ServerNameHelper {

    /** Private constructor */
    private ServerNameHelper() {
        super();
    }

    /**
     * This regex matches dotted-quad IPv4 addresses, like 123.123.123.123
     */
    @SuppressWarnings("CheckStyle")
    private static final Pattern V4ADDR = Pattern.compile(
        "^(\\d{1,2}|1\\d\\d|2[0-4]\\d|25[0-5])\\.(\\d{1,2}|1\\d\\d|2[0-4]\\d|25[0-5])\\.(\\d{1,2}|1\\d\\d|2[0-4]\\d"
            + "|25[0-5])\\.(\\d{1,2}|1\\d\\d|2[0-4]\\d|25[0-5])$");

    /**
     * This monster matches all valid IPv6 addresses, including the embedded dotted-quad notation
     */
    @SuppressWarnings("CheckStyle")
    private static final Pattern V6ADDR = Pattern.compile(
        "^(^(([0-9A-Fa-f]{1,4}(((:[0-9A-Fa-f]{1,4}){5}::[0-9A-Fa-f]{1,4})|((:[0-9A-Fa-f]{1,4}){4}::[0-9A-Fa-f]{1,4}(:"
            + "[0-9A-Fa-f]{1,4}){0,1})|((:[0-9A-Fa-f]{1,4}){3}::[0-9A-Fa-f]{1,4}(:[0-9A-Fa-f]{1,4}){0,2})|((:"
            + "[0-9A-Fa-f]{1,4}){2}::[0-9A-Fa-f]{1,4}(:[0-9A-Fa-f]{1,4}){0,3})|(:[0-9A-Fa-f]{1,4}::[0-9A-Fa-f]"
            + "{1,4}(:[0-9A-Fa-f]{1,4}){0,4})|(::[0-9A-Fa-f]{1,4}(:[0-9A-Fa-f]{1,4}){0,5})|(:[0-9A-Fa-f]{1,4}){7}))$"
            + "|^(::[0-9A-Fa-f]{1,4}(:[0-9A-Fa-f]{1,4}){0,6})$)|^::$)|^(^(([0-9A-Fa-f]{1,4}((((:[0-9A-Fa-f]{1,4}){4}:)"
            + "|(:[0-9A-Fa-f]{1,4}){3}:(:[0-9A-Fa-f]{1,4}){0,1})|((:[0-9A-Fa-f]{1,4}){2}:(:[0-9A-Fa-f]{1,4}){0,2})|"
            + "((:[0-9A-Fa-f]{1,4}):(:[0-9A-Fa-f]{1,4}){0,3})|(:(:[0-9A-Fa-f]{1,4}){0,4})|(:[0-9A-Fa-f]{1,4}){5}))|"
            + "^(::[0-9A-Fa-f]{1,4}(:[0-9A-Fa-f]{1,4}){0,4}))|^:):((25[0-5]|2[0-4][0-9]|1[0-9]{2}|[0-9][0-9]|[0-9])"
            + "\\.){3}(25[0-5]|2[0-4][0-9]|[0-1]?[0-9]{0,2})$");

    /**
     *  This regex matches NetBIOS names (see http://support.microsoft.com/kb/909264)
     */
    private static final Pattern NETBIOS = Pattern.compile("^([^\\\\/:*?\"<>|.]{1,15})$");

    /**
     * This regex matches DNS names (see http://support.microsoft.com/kb/909264)
     */
    @SuppressWarnings("CheckStyle")
    private static final Pattern FQDN = Pattern.compile(
        "^(([a-zA-Z0-9]|[a-zA-Z0-9][^\\s,~:!@#$%\\^&'\\.\\(\\)\\{\\}_]*[^\\s,~:!@#$%\\^&'\\.\\(\\)\\{\\}_\\-])\\.)*"
            + "([a-zA-Z0-9]|[a-zA-Z0-9][^\\s,~:!@#$%\\^&'\\.\\(\\)\\{\\}_]*[^\\s,~:!@#$%\\^&'\\.\\(\\)\\{\\}_\\-])$");

    public static boolean isValidIPAddress(String ipAddress) {
        return (ipAddress != null && !ipAddress.isEmpty()
            && (isValidIPv4Address(ipAddress) || isValidIPv6Address(ipAddress)));
    }

    private static boolean isValidIPv4Address(String ipAddress) {
        return (ipAddress != null && !ipAddress.isEmpty() && V4ADDR.matcher(ipAddress).matches());
    }

    public static boolean isValidIPv6Address(String ipAddress) {
        return (ipAddress != null && !ipAddress.isEmpty() && V6ADDR.matcher(ipAddress).matches());
    }

    private static boolean isValidNetBiosName(String hostName) {
        return (hostName != null && !hostName.isEmpty() && NETBIOS.matcher(hostName).matches());
    }

    private static boolean isValidFQDN(String hostName) {
        return (hostName != null && !hostName.isEmpty() && FQDN.matcher(hostName).matches());
    }

    public static boolean isValidHostNameOrIPv4Address(String hostName) {
        return (isValidIPv4Address(hostName) || isValidNetBiosName(hostName) || isValidFQDN(hostName));
    }

    public static boolean isValidHostNameOrIPAddress(String hostName) {
        return (isValidIPv4Address(hostName) || isValidIPv6Address(hostName)
            || isValidNetBiosName(hostName) || isValidFQDN(hostName));
    }
}
