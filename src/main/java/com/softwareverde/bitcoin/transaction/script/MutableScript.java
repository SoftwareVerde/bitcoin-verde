package com.softwareverde.bitcoin.transaction.script;

import com.softwareverde.bitcoin.util.ByteUtil;

public class MutableScript implements Script {
    protected final byte[] _bytes;
    protected int _index = 0;
    protected Boolean _didOverflow = false;

    protected byte _getNextByte(final Boolean shouldConsumeByte) {
        if (_index >= _bytes.length) {
            _didOverflow = true;
            return 0x00;
        }

        final byte b = _bytes[(_bytes.length - _index) - 1];

        if (shouldConsumeByte) {
            _index += 1;
        }

        return b;
    }

    public MutableScript(final byte[] bytes) {
        _bytes = new byte[bytes.length];
        ByteUtil.setBytes(_bytes, bytes);
    }

    public MutableScript(final Script script) {
        if (script instanceof MutableScript) {
            _bytes = ((MutableScript) script)._bytes;
            return;
        }

        final byte[] bytes = script.getBytes();
        _bytes = new byte[bytes.length];
        ByteUtil.setBytes(_bytes, bytes);
    }

    @Override
    public byte peakNextByte() {
        return _getNextByte(false);
    }

    @Override
    public byte getNextByte() {
        return _getNextByte(true);
    }

    @Override
    public byte[] getNextBytes(final Integer byteCount) {
        final byte[] bytes = new byte[byteCount];
        for (int i=0; i<byteCount; ++i) {
            bytes[i] = _getNextByte(true);
        }
        return bytes;
    }

    @Override
    public Boolean hasNextByte() {
        return (_index < _bytes.length);
    }

    @Override
    public void resetPosition() {
        _index = 0;
    }

    @Override
    public int getByteCount() {
        return _bytes.length;
    }

    @Override
    public Boolean didOverflow() {
        return _didOverflow;
    }

    @Override
    public byte[] getBytes() {
        return ByteUtil.copyBytes(_bytes);
    }

    @Override
    public void removeSignatures() {

    }
}
