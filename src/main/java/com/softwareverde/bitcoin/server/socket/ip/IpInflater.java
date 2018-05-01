package com.softwareverde.bitcoin.server.socket.ip;

public class IpInflater {

    public Ip fromString(final String string) {
        if (string == null) { return null; }
        if (string.matches("[^0-9:.]")) { return null; }

        final Boolean isIpv4 = string.matches("^[0-9]+.[0-9]+.[0-9]+.[0-9]+$");
        if (isIpv4) {
            return Ipv4.parse(string);
        }
        else {
            return Ipv6.parse(string);
        }
    }
}
