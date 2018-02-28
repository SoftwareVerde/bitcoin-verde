package com.softwareverde.bitcoin.transaction.script.stack;

import com.softwareverde.bitcoin.util.ByteUtil;

public class Value {
    public static Value fromInteger(final Integer integerValue) {
        final byte[] bytes = new byte[4];
        ByteUtil.setBytes(bytes, ByteUtil.integerToBytes(integerValue));
        return new Value(bytes);
    }

    public static Value fromBoolean(final Boolean booleanValue) {
        final byte[] bytes = new byte[4];
        bytes[bytes.length - 1] = (byte) (booleanValue ? 0x01 : 0x00);
        return new Value(bytes);
    }

    public static Value fromBytes(final byte[] bytes) {
        return new Value(bytes);
    }

    protected byte[] _bytes;

    protected Value(final byte[] bytes) {
        _bytes = ByteUtil.copyBytes(bytes);
    }

    public Integer asInteger() {
        return ByteUtil.bytesToInteger(_bytes);
    }

    public Long asLong() {
        return ByteUtil.bytesToLong(_bytes);
    }

    public Boolean asBoolean() {
        for (int i=0; i<_bytes.length; ++i) {
            if (_bytes[i] != 0x00) { return true; }
        }
        return false;
    }

    public ScriptSignature asScriptSignature() {
        return ScriptSignature.fromBytes(ByteUtil.copyBytes(_bytes));
    }

    public byte[] getBytes() {
        return ByteUtil.copyBytes(_bytes);
    }
}
