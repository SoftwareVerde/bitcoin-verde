package com.softwareverde.bitcoin.transaction.script.stack;

import com.softwareverde.bitcoin.util.ByteUtil;

public class Value {
    public static Value fromInteger(final Integer integerValue) {
        final byte[] bytes = new byte[4];
        ByteUtil.setBytes(bytes, ByteUtil.integerToBytes(integerValue));
        return new Value(bytes);
    }

    protected byte[] _bytes;

    public Value(final byte[] bytes) {
        _bytes = ByteUtil.copyBytes(bytes);
    }

    public Integer asInteger() {
        return ByteUtil.bytesToInteger(_bytes);
    }

    public Long asLong() {
        return ByteUtil.bytesToLong(_bytes);
    }

    public byte[] getBytes() {
        return ByteUtil.copyBytes(_bytes);
    }
}
