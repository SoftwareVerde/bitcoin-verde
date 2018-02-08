package com.softwareverde.bitcoin.util.bytearray;

import java.util.ArrayList;
import java.util.List;

public class ByteArrayBuilder {
    private final List<byte[]> _byteArrays = new ArrayList<byte[]>();
    private Integer _totalByteCount = 0;

    public void appendBytes(final byte[] bytes, final Endian endian) {
        final byte[] copiedBytes = new byte[bytes.length];
        for (int i=0; i<bytes.length; ++i) {
            final Integer readIndex = ( (endian == Endian.BIG) ? (i) : ((bytes.length - i) - 1) );
            copiedBytes[i] = bytes[readIndex];
        }

        _byteArrays.add(copiedBytes);
        _totalByteCount += bytes.length;
    }

    public byte[] build() {
        final byte[] data = new byte[_totalByteCount];

        int offset = 0;
        for (final byte[] value : _byteArrays) {
            for (int i = 0; i < value.length; ++i) {
                data[offset + i] = value[i];
            }
            offset += value.length;
        }

        return data;
    }
}