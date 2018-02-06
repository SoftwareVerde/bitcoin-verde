package com.softwareverde.bitcoin.server.socket.message.networkaddress.ip;

import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.util.StringUtil;

import java.util.List;

public class Ipv6 implements Ip {

    protected static byte[] _parse(final String string) {
        final String trimmedString = string.trim();
        final String strippedIp = trimmedString.replaceAll("[^0-9A-Fa-f:]", "");
        final Boolean stringContainedInvalidCharacters = (strippedIp.length() != trimmedString.length());
        if (stringContainedInvalidCharacters) { return null; }

        final List<String> ipSegmentStrings = StringUtil.pregMatch("^([0-9A-Fa-f]*):([0-9A-Fa-f]*):([0-9A-Fa-f]*):([0-9A-Fa-f]*):([0-9A-Fa-f]*):([0-9A-Fa-f]*):([0-9A-Fa-f]*):([0-9A-Fa-f]*)$", strippedIp);
        final Integer ipSegmentStringCount = ipSegmentStrings.size();
        if (ipSegmentStringCount != 8) { return null; }

        final byte[] ipSegmentBytes = new byte[16];
        for (int i=0; i<8; ++i) {
            final String ipSegmentString;
            {
                final String originalIpSegmentString = ipSegmentStrings.get(i);
                final Integer availableCharCount = originalIpSegmentString.length();
                final char[] charArray = new char[4];
                for (int j=0; j<charArray.length; ++j) {
                    final char c = (j < availableCharCount ? originalIpSegmentString.charAt((availableCharCount - j) - 1) : '0');
                    charArray[(charArray.length - j) - 1] = c;
                }
                ipSegmentString = new String(charArray);
            }
            final byte[] segmentBytes = BitcoinUtil.hexStringToByteArray(ipSegmentString);
            if ((segmentBytes == null) || (segmentBytes.length != 2)) {
                return null;
            }

            ipSegmentBytes[(i * 2)] = segmentBytes[0];
            ipSegmentBytes[(i * 2) + 1] = segmentBytes[1];
        }
        return ipSegmentBytes;
    }

    public static Ipv6 parse(final String string) {
        final byte[] segments = _parse(string);
        if (segments == null) { return null; }

        final Ipv6 ipv6 = new Ipv6();
        for (int i=0; i<segments.length; ++i) {
            ipv6._segments[i] = segments[i];
        }
        return ipv6;
    }

    private final byte[] _segments = new byte[16];

    @Override
    public byte[] getBytes() {
        final byte[] bytes = new byte[16];
        for (int i=0; i<_segments.length; ++i) {
            bytes[i] = _segments[i];
        }
        return bytes;
    }
}
