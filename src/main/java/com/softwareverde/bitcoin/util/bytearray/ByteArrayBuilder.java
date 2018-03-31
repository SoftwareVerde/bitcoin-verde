package com.softwareverde.bitcoin.util.bytearray;

import com.softwareverde.constable.bytearray.ByteArray;

import java.util.ArrayList;
import java.util.List;

public class ByteArrayBuilder {
    private final List<byte[]> _byteArrays = new ArrayList<byte[]>();
    private Integer _totalByteCount = 0;

    protected void _appendBytes(final byte[] bytes, final Endian endian) {
        final byte[] copiedBytes = new byte[bytes.length];
        for (int i=0; i<bytes.length; ++i) {
            final Integer readIndex = ( (endian == Endian.BIG) ? (i) : ((bytes.length - i) - 1) );
            copiedBytes[i] = bytes[readIndex];
        }

        _byteArrays.add(copiedBytes);
        _totalByteCount += bytes.length;
    }

    protected void _appendByte(final byte b) {
        _byteArrays.add(new byte[] { b });
        _totalByteCount += 1;
    }

    public void appendBytes(final byte[] bytes, final Endian endian) {
        _appendBytes(bytes, endian);
    }

    public void appendBytes(final byte[] bytes) {
        _appendBytes(bytes, Endian.BIG);
    }

    public void appendBytes(final ByteArray bytes, final Endian endian) {
        _appendBytes(bytes.getBytes(), endian);
    }

    public void appendBytes(final ByteArray byteArray) {
        _appendBytes(byteArray.getBytes(), Endian.BIG);
    }

    public void appendByte(final byte b) {
        _appendByte(b);
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

    public Integer getByteCount() {
        return _totalByteCount;
    }

    public void clear() {
        _totalByteCount = 0;
        _byteArrays.clear();
    }
}