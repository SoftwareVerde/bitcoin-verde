package com.softwareverde.bitcoin.server.socket.ip;

import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.util.StringUtil;
import com.softwareverde.util.Util;

import java.util.List;

public class Ipv4 implements Ip {
    protected static byte[] _parse(final String string) {
        final String trimmedString = string.trim();
        final String strippedIp = trimmedString.replaceAll("[^0-9\\.]", "");
        final Boolean stringContainedInvalidCharacters = (strippedIp.length() != trimmedString.length());
        if (stringContainedInvalidCharacters) { return null; }

        final List<String> ipSegments = StringUtil.pregMatch("^([0-9]+)\\.([0-9]+)\\.([0-9]+)\\.([0-9]+)$", strippedIp);
        final byte[] bytes = new byte[ipSegments.size()];
        for (int i=0; i<bytes.length; ++i) {
            final String ipSegment = ipSegments.get(i);
            final Integer intValue = Util.parseInt(ipSegment);
            if (intValue > 255) { return null; }
            bytes[i] = (byte) intValue.intValue();
        }
        return bytes;
    }

    public static Ipv4 fromBytes(final byte[] bytes) {
        if (bytes.length != 4) { return null; }

        final Ipv4 ipv4 = new Ipv4();
        ByteUtil.setBytes(ipv4._bytes, bytes);
        return ipv4;
    }

    public static Ipv4 parse(final String string) {
        if (string == null) { return null; }

        final byte[] segments = _parse(string);
        if (segments == null) { return null; }
        if (segments.length != 4) { return null; }

        final Ipv4 ipv4 = new Ipv4();
        for (int i=0; i<segments.length; ++i) {
            ipv4._bytes[i] = segments[i];
        }
        return ipv4;
    }

    private final byte[] _bytes = new byte[4];

    public Ipv4() { }

    public Ipv4(final byte[] ipByteSegments) {
        if (ipByteSegments.length == _bytes.length) {
            for (int i = 0; i< _bytes.length; ++i) {
                _bytes[i] = ipByteSegments[i];
            }
        }
    }

    @Override
    public byte[] getBytes() {
        final byte[] bytes = new byte[4];
        for (int i = 0; i< _bytes.length; ++i) {
            bytes[i] = _bytes[i];
        }
        return bytes;
    }

    @Override
    public Ip duplicate() {
        final Ipv4 ipv4 = new Ipv4();
        for (int i = 0; i< _bytes.length; ++i) {
            ipv4._bytes[i] = _bytes[i];
        }
        return ipv4;
    }
}
