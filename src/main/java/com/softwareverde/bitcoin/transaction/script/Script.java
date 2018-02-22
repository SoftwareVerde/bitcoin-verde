package com.softwareverde.bitcoin.transaction.script;

import com.softwareverde.bitcoin.type.bytearray.ByteArray;
import com.softwareverde.bitcoin.util.ByteUtil;

public class Script implements ByteArray {
    protected final byte[] _bytes;
    protected int _index = 0;

    protected byte _getNextByte(final Boolean shouldConsumeByte) {
        if (_index >= _bytes.length) { return 0x00; }

        final byte b = _bytes[_index];

        if (shouldConsumeByte) {
            _index += 1;
        }

        return b;
    }

    public Script(final byte[] bytes) {
        _bytes = new byte[bytes.length];
        ByteUtil.setBytes(_bytes, bytes);
    }

    public byte peakNextByte() {
        return _getNextByte(false);
    }

    public byte getNextByte() {
        return _getNextByte(true);
    }

    public byte[] getNextBytes(final Integer byteCount) {
        final byte[] bytes = new byte[byteCount];
        for (int i=0; i<byteCount; ++i) {
            bytes[i] = _getNextByte(true);
        }
        return bytes;
    }

    public Boolean hasNextByte() {
        return (_index < _bytes.length);
    }

    public void resetPosition() {
        _index = 0;
    }

    @Override
    public byte[] getBytes() {
        return ByteUtil.copyBytes(_bytes);
    }
}
