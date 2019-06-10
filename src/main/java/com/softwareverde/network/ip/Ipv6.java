package com.softwareverde.network.ip;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.ImmutableByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.StringUtil;
import com.softwareverde.util.Util;

public class Ipv6 implements Ip {
    public static final Integer BYTE_COUNT = 16;

    protected static byte[] _createIpv4CompatibleIpv6(final Ipv4 ipv4) {
        final byte[] ipSegmentBytes = new byte[BYTE_COUNT];
        final ByteArray ipv4Bytes = ipv4.getBytes();
        ipSegmentBytes[10] = (byte) 0xFF;
        ipSegmentBytes[11] = (byte) 0xFF;

        final int offset = (BYTE_COUNT - Ipv4.BYTE_COUNT);
        for (int i = 0; i < ipv4Bytes.getByteCount(); ++i) {
            ipSegmentBytes[offset + i] = ipv4Bytes.getByte(i);
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

                for (int i = 0; i < 8; ++i) {
                    ipSegmentStrings[i] = splitIpSegments[i];
                }
            }
        }

        final byte[] ipSegmentBytes = new byte[BYTE_COUNT];
        for (int i = 0; i < ipSegmentStrings.length; ++i) {
            final String ipSegmentString;
            {
                final String originalIpSegmentString = ipSegmentStrings[i];
                final Integer availableCharCount = originalIpSegmentString.length();
                final char[] charArray = new char[4];
                for (int j = 0; j < charArray.length; ++j) {
                    final char c = (j < availableCharCount ? originalIpSegmentString.charAt((availableCharCount - j) - 1) : '0');
                    charArray[(charArray.length - j) - 1] = c;
                }
                ipSegmentString = new String(charArray);
            }
            final byte[] segmentBytes = HexUtil.hexStringToByteArray(ipSegmentString);
            if ( (segmentBytes == null) || (segmentBytes.length != 2) ) {
                return null;
            }

            ipSegmentBytes[(i * 2)] = segmentBytes[0];
            ipSegmentBytes[(i * 2) + 1] = segmentBytes[1];
        }
        return ipSegmentBytes;
    }

    public static Ipv6 fromBytes(final byte[] bytes) {
        if (bytes.length == Ipv4.BYTE_COUNT) { return Ipv6.createIpv4CompatibleIpv6(Ipv4.fromBytes(bytes)); }
        if (bytes.length != BYTE_COUNT) { return null; }

        return new Ipv6(bytes);
    }

    public static Ipv6 parse(final String string) {
        final byte[] segments = _parse(string);
        if (segments == null) { return null; }

        return new Ipv6(segments);
    }

    public static Ipv6 createIpv4CompatibleIpv6(final Ipv4 ipv4) {
        final byte[] bytes = _createIpv4CompatibleIpv6(ipv4);
        return new Ipv6(bytes);
    }

    private final ByteArray _bytes;

    public Ipv6() {
        _bytes = new MutableByteArray(BYTE_COUNT);
    }

    public Ipv6(final byte[] bytes) {
        if (bytes.length == 16) {
            _bytes = new ImmutableByteArray(bytes);
        }
        else {
            _bytes = new MutableByteArray(16);
        }
    }

    @Override
    public ByteArray getBytes() {
        return _bytes;
    }

    @Override
    public String toString() {
        final StringBuilder stringBuilder = new StringBuilder();
        String separator = "";
        for (int i = 0; i < BYTE_COUNT; i += 2) {
            final byte[] segmentBytes = _bytes.getBytes(i, 2);
            final String segmentString = HexUtil.toHexString(segmentBytes);

            stringBuilder.append(separator);
            stringBuilder.append(segmentString);

            separator = ":";
        }
        return stringBuilder.toString();
    }

    @Override
    public boolean equals(final Object object) {
        if (object == null) { return false; }
        if (! (object instanceof Ipv6)) { return false; }
        final Ipv6 ipv6 = (Ipv6) object;
        return Util.areEqual(_bytes, ipv6._bytes);
    }

    @Override
    public int hashCode() {
        return _bytes.hashCode();
    }
}
