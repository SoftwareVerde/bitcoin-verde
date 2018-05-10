package com.softwareverde.network.ip;

import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.StringUtil;

import java.util.Arrays;

public class Ipv6 implements Ip {

    protected static byte[] _createIpv4CompatibleIpv6(final Ipv4 ipv4) {
        final byte[] ipSegmentBytes = new byte[16];
        final byte[] ipv4Bytes = ipv4.getBytes();
        ipSegmentBytes[10] = (byte) 0xFF;
        ipSegmentBytes[11] = (byte) 0xFF;
        for (int i=0; i<ipv4Bytes.length; ++i) {
            ipSegmentBytes[12 + i] = ipv4Bytes[i];
        }
        return ipSegmentBytes;
    }

    protected static byte[] _parse(final String string) {
        final String trimmedString = string.trim();

        final Boolean matchesIpv4CompatibilityMode = (! StringUtil.pregMatch("^::[fF]{4}:([0-9]+)\\.([0-9]+)\\.([0-9]+)\\.([0-9]+)$", trimmedString).isEmpty());
        if (matchesIpv4CompatibilityMode) {
            final Integer offset = "::FFFF:".length();
            final Ipv4 ipv4 = Ipv4.parse(trimmedString.substring(offset));
            if (ipv4 == null) { return null; }

            return _createIpv4CompatibleIpv6(ipv4);
        }

        final String strippedIpString = trimmedString.replaceAll("[^0-9A-Fa-f:]", "");
        final Boolean stringContainedInvalidCharacters = (strippedIpString.length() != trimmedString.length());
        if (stringContainedInvalidCharacters) { return null; }

        final String[] ipSegmentStrings = new String[8];
        {
            final Boolean containsShorthandMarker = (strippedIpString.contains("::"));
            if (containsShorthandMarker) {
                final String firstHalf;
                final String secondHalf;
                {
                    final String[] halves = strippedIpString.split("::", 2);
                    firstHalf = halves[0];
                    secondHalf = halves[1];
                }

                final Boolean containsMultipleShorthandMarkers = ( (firstHalf.contains("::")) || (secondHalf.contains("::")) );
                if ( containsMultipleShorthandMarkers ) { return null; } // Ipv6 may only have one shorthand-marker.

                {
                    final String[] firstHalfSegments = firstHalf.split(":");
                    final String[] secondHalfSegments = secondHalf.split(":");

                    final Boolean containsTooManySegments = ((firstHalfSegments.length + secondHalfSegments.length) >= 8);
                    if (containsTooManySegments) { return null; }

                    for (int i=0; i < firstHalfSegments.length; ++i) {
                        ipSegmentStrings[i] = firstHalfSegments[i];
                    }

                    for (int i=0; i < (8 - firstHalfSegments.length - secondHalfSegments.length); ++i) {
                        ipSegmentStrings[firstHalfSegments.length + i] = "0";
                    }

                    for (int i=0; i < secondHalfSegments.length; ++i) {
                        ipSegmentStrings[(ipSegmentStrings.length - i) - 1] = secondHalfSegments[(secondHalfSegments.length - i) - 1];
                    }
                }
            }
            else {
                final String[] splitIpSegments = strippedIpString.split(":");
                if (splitIpSegments.length != 8) { return null; }
                for (int i=0; i<8; ++i) {
                    ipSegmentStrings[i] = splitIpSegments[i];
                }
            }
        }

        final byte[] ipSegmentBytes = new byte[16];
        for (int i=0; i<8; ++i) {
            final String ipSegmentString;
            {
                final String originalIpSegmentString = ipSegmentStrings[i];
                final Integer availableCharCount = originalIpSegmentString.length();
                final char[] charArray = new char[4];
                for (int j=0; j<charArray.length; ++j) {
                    final char c = (j < availableCharCount ? originalIpSegmentString.charAt((availableCharCount - j) - 1) : '0');
                    charArray[(charArray.length - j) - 1] = c;
                }
                ipSegmentString = new String(charArray);
            }
            final byte[] segmentBytes = HexUtil.hexStringToByteArray(ipSegmentString);
            if ((segmentBytes == null) || (segmentBytes.length != 2)) {
                return null;
            }

            ipSegmentBytes[(i * 2)] = segmentBytes[0];
            ipSegmentBytes[(i * 2) + 1] = segmentBytes[1];
        }
        return ipSegmentBytes;
    }

    public static Ipv6 fromBytes(final byte[] bytes) {
        if (bytes.length == 4) { return createIpv4CompatibleIpv6(Ipv4.fromBytes(bytes)); }
        if (bytes.length != 16) { return null; }

        final Ipv6 ipv6 = new Ipv6();
        ByteUtil.setBytes(ipv6._bytes, bytes);
        return ipv6;
    }

    public static Ipv6 parse(final String string) {
        final byte[] segments = _parse(string);
        if (segments == null) { return null; }

        final Ipv6 ipv6 = new Ipv6();
        for (int i=0; i<segments.length; ++i) {
            ipv6._bytes[i] = segments[i];
        }
        return ipv6;
    }

    public static Ipv6 createIpv4CompatibleIpv6(final Ipv4 ipv4) {
        final Ipv6 ipv6 = new Ipv6();
        final byte[] bytes = _createIpv4CompatibleIpv6(ipv4);
        for (int i=0; i<bytes.length; ++i) {
            ipv6._bytes[i] = bytes[i];
        }
        return ipv6;
    }

    private final byte[] _bytes = new byte[16];

    @Override
    public byte[] getBytes() {
        final byte[] bytes = new byte[16];
        for (int i = 0; i< _bytes.length; ++i) {
            bytes[i] = _bytes[i];
        }
        return bytes;
    }

    @Override
    public Ip copy() {
        final Ipv6 ipv6 = new Ipv6();
        for (int i=0; i<_bytes.length; ++i) {
            ipv6._bytes[i] = _bytes[i];
        }
        return ipv6;
    }

    @Override
    public String toString() {
        final StringBuilder stringBuilder = new StringBuilder();
        for (final byte b : _bytes) {
            stringBuilder.append(ByteUtil.byteToInteger(b));
            stringBuilder.append(":");
        }
        stringBuilder.deleteCharAt(stringBuilder.length() - 1); // Remove the last colon...
        return stringBuilder.toString();
    }

    @Override
    public boolean equals(final Object object) {
        if (object == null) { return false; }
        if (! (object instanceof Ipv6)) { return false; }
        final Ipv6 ipv6 = (Ipv6) object;
        return ByteUtil.areEqual(_bytes, ipv6._bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(_bytes);
    }
}
