package com.softwareverde.bitcoin.util.bytearray;

import com.softwareverde.bitcoin.util.ByteUtil;

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

    public void appendByte(final byte b) {
        _appendByte(b);
    }

    public void writeVariableSizedInteger(final long value) {
        final byte[] bytes = ByteUtil.longToBytes(value);

        if (value < 0xFDL) {
            _appendByte(bytes[7]);
        }
        else if (value < 0xFFFFL) {
            _appendByte((byte) 0xFD);
            _appendByte(bytes[7]);
            _appendByte(bytes[6]);
        }
        else if (value < 0xFFFFFFFFL) {
            _appendByte((byte) 0xFE);
            _appendByte(bytes[7]);
            _appendByte(bytes[6]);
            _appendByte(bytes[5]);
            _appendByte(bytes[4]);
        }
        else {
            _appendByte((byte) 0xFF);
            _appendBytes(bytes, Endian.LITTLE);
        }
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