package com.softwareverde.network.ip;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.ImmutableByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.StringUtil;
import com.softwareverde.util.Util;

import java.util.List;

public class Ipv4 implements Ip {
    public static final Integer BYTE_COUNT = 4;

    protected static byte[] _parse(final String string) {
        final String trimmedString = string.trim();
        final String strippedIp = trimmedString.replaceAll("[^0-9\\.]", "");
        final boolean stringContainedInvalidCharacters = (strippedIp.length() != trimmedString.length());
        if (stringContainedInvalidCharacters) { return null; }

        final List<String> ipSegments = StringUtil.pregMatch("^([0-9]+)\\.([0-9]+)\\.([0-9]+)\\.([0-9]+)$", strippedIp);
        final byte[] bytes = new byte[ipSegments.size()];
        for (int i = 0; i < bytes.length; ++i) {
            final String ipSegment = ipSegments.get(i);
            final Integer intValue = Util.parseInt(ipSegment);
            if (intValue > 255) { return null; }
            bytes[i] = (byte) intValue.intValue();
        }
        return bytes;
    }

    public static Ipv4 fromBytes(final byte[] bytes) {
        if (bytes.length != 4) { return null; }

        return new Ipv4(bytes);
    }

    public static Ipv4 parse(final String string) {
        if (string == null) { return null; }

        final byte[] segments = _parse(string);
        if (segments == null) { return null; }
        if (segments.length != BYTE_COUNT) { return null; }

        return new Ipv4(segments);
    }

    private final ByteArray _bytes;

    public Ipv4() {
        _bytes = new MutableByteArray(BYTE_COUNT);
    }

    public Ipv4(final byte[] ipByteSegments) {
        if (ipByteSegments.length == BYTE_COUNT) {
            _bytes = new ImmutableByteArray(ipByteSegments);
        }
        else {
            _bytes = new MutableByteArray(BYTE_COUNT);
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
        for (int i = 0; i < BYTE_COUNT; ++i) {
            final byte b = _bytes.getByte(i);
            final int byteInteger = ByteUtil.byteToInteger(b);

            stringBuilder.append(separator);
            stringBuilder.append(byteInteger);

            separator = ".";
        }
        return stringBuilder.toString();
    }

    @Override
    public boolean equals(final Object object) {
        if (object == null) { return false; }
        if (! (object instanceof Ipv4)) { return false; }
        final Ipv4 ipv4 = (Ipv4) object;
        return Util.areEqual(_bytes, ipv4._bytes);
    }

    @Override
    public int hashCode() {
        return _bytes.hashCode();
    }
}
