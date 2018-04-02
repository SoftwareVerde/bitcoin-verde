package com.softwareverde.bitcoin.transaction.script.stack;

import com.softwareverde.constable.Const;
import com.softwareverde.constable.bytearray.ImmutableByteArray;
import com.softwareverde.util.ByteUtil;

public class Value extends ImmutableByteArray implements Const {
    public static Value fromInteger(final Integer integerValue) {
        final byte[] bytes = new byte[4];
        ByteUtil.setBytes(bytes, ByteUtil.integerToBytes(integerValue));
        return new Value(bytes);
    }

    public static Value fromBoolean(final Boolean booleanValue) {
        final byte[] bytes = new byte[4];
        bytes[bytes.length - 1] = (booleanValue ? (byte) 0x01 : (byte) 0x00);
        return new Value(bytes);
    }

    public static Value fromBytes(final byte[] bytes) {
        return new Value(bytes);
    }

    protected static boolean _isNegativeNumber(final byte[] bytes) {
        final byte mostSignificantByte = bytes[bytes.length - 1];
        return ( (mostSignificantByte & ((byte) 0x80)) != ((byte) 0x00) );
    }

    protected Value(final byte[] bytes) {
        super(bytes);
    }

    /**
     * Interprets _bytes as a signed, little-endian, variable-length integer value.
     * If _bytes is empty, zero is returned.
     */
    public Integer asInteger() {
        if (_bytes.length == 0) { return 0; }

        final boolean isNegative = _isNegativeNumber(_bytes);

        final byte[] bigEndianBytes = ByteUtil.reverseEndian(_bytes);
        { // Remove the sign bit... (only matters when _bytes.length is less than the byteCount of an integer)
            bigEndianBytes[0] &= (byte) 0x7F;
        }

        final Integer value = ByteUtil.bytesToInteger(bigEndianBytes);
        return (isNegative ? -value : value);
    }

    public Long asLong() {
        if (_bytes.length == 0) { return 0L; }

        final boolean isNegative = _isNegativeNumber(_bytes);

        final byte[] bigEndianBytes = ByteUtil.reverseEndian(_bytes);
        { // Remove the sign bit... (only matters when _bytes.length is less than the byteCount of a long)
            bigEndianBytes[0] &= (byte) 0x7F;
        }

        final Long value = ByteUtil.bytesToLong(bigEndianBytes);
        return (isNegative ? -value : value);
    }

    public Boolean asBoolean() {
        if (_bytes.length == 0) { return false; }

        for (int i=0; i<_bytes.length; ++i) {
            if ( (i == (_bytes.length - 1)) && (_bytes[i] == (byte) 0x80) ) { continue; } // Negative zero can still be false... (Little-Endian)
            if (_bytes[i] != 0x00) { return true; }
        }
        return false;
    }

    public ScriptSignature asScriptSignature() {
        return ScriptSignature.fromBytes(ByteUtil.copyBytes(_bytes));
    }

    @Override
    public Value asConst() {
        return this;
    }
}
