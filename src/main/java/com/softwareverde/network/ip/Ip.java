package com.softwareverde.network.ip;

import com.softwareverde.constable.Const;
import com.softwareverde.constable.bytearray.ByteArray;

import java.net.*;

public interface Ip extends Const {
    static Ip fromSocket(final Socket socket) {
        final SocketAddress socketAddress = socket.getRemoteSocketAddress();
        if (socketAddress instanceof InetSocketAddress) {
            final InetAddress inetAddress = ((InetSocketAddress) socketAddress).getAddress();
            if (inetAddress instanceof Inet4Address) {
                final Inet4Address inet4Address = (Inet4Address) inetAddress;
                return Ipv4.fromBytes(inet4Address.getAddress());
            }
            else if (inetAddress instanceof Inet6Address) {
                final Inet6Address inet6Address = (Inet6Address) inetAddress;
                return Ipv6.fromBytes(inet6Address.getAddress());
            }
        }

        return null;
    }

    static Ip fromString(final String string) {
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

    static Ip fromHostName(final String hostName) {
        try {
            final InetAddress inetAddress = InetAddress.getByName(hostName);
            if (inetAddress instanceof Inet4Address) {
                final Inet4Address inet4Address = (Inet4Address) inetAddress;
                return Ipv4.fromBytes(inet4Address.getAddress());
            }
            else if (inetAddress instanceof Inet6Address) {
                final Inet6Address inet6Address = (Inet6Address) inetAddress;
                return Ipv6.fromBytes(inet6Address.getAddress());
            }
        }
        catch (final UnknownHostException exception) { }

        return null;
    }

    static Ip fromStringOrHost(final String ipOrHostName) {
        final Ip ipFromString = Ip.fromString(ipOrHostName);
        if (ipFromString != null) {
            return ipFromString;
        }

        final Ip ipFromHost = Ip.fromHostName(ipOrHostName);
        if (ipFromHost != null) {
            return ipFromHost;
        }

        return null;
    }

    ByteArray getBytes();

    @Override
    String toString();
}
