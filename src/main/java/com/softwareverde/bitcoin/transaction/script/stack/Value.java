package com.softwareverde.bitcoin.transaction.script.stack;

import com.softwareverde.bitcoin.transaction.script.signature.ScriptSignature;
import com.softwareverde.bitcoin.type.key.PublicKey;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.constable.Const;
import com.softwareverde.constable.bytearray.ImmutableByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.StringUtil;

public class Value extends ImmutableByteArray implements Const {
    public static Integer MAX_BYTE_COUNT = 520; // https://en.bitcoin.it/wiki/Script#Arithmetic

    // NOTE: Bitcoin uses "MPI" encoding for its numeric values on the stack.
    //  This fact and/or a specification for how MPI is encoded is not on the wiki (...of course).
    //  It appears MPI is a minimum-byte-encoding, with a sign bit if negative, similar(ish) to DER encoding.
    //  Ex: -65280
    //      MPI:            0x80FF00
    //      Signed Hex:     -0xFF00
    //      2's Complement: 0xFFFFFFFFFFFF0100
    protected static byte[] _longToBytes(final Long value) {
        final boolean isNegative = (value < 0);

        final long absValue = Math.abs(value);
        final int unsignedByteCount = ( (BitcoinUtil.log2((int) absValue) / 8) + 1 );
        final byte[] absValueBytes = ByteUtil.integerToBytes(absValue);

        final boolean requiresSignPadding;
        if (isNegative) {
            requiresSignPadding = ((absValueBytes[absValueBytes.length - unsignedByteCount] & 0x80) == 0x80);
        }
        else {
            requiresSignPadding = ((absValueBytes[absValueBytes.length - unsignedByteCount] & 0x80) == 0x80);
        }

        final byte[] bytes = new byte[(requiresSignPadding ? unsignedByteCount + 1 : unsignedByteCount)];
        ByteUtil.setBytes(bytes, ByteUtil.reverseEndian(absValueBytes));
        if (isNegative) {
            bytes[bytes.length - 1] |= (byte) 0x80;
        }

        return bytes;
    }

    public static Value fromInteger(final Long longValue) {
        return new Value(ByteUtil.reverseEndian(_longToBytes(longValue)));
    }

    public static Value fromBoolean(final Boolean booleanValue) {
        final byte[] bytes = new byte[4];
        bytes[0] = (booleanValue ? (byte) 0x01 : (byte) 0x00);
        return new Value(bytes);
    }

    public static Value fromBytes(final byte[] bytes) {
        if (bytes.length > MAX_BYTE_COUNT) { return null; }
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
        return ScriptSignature.fromBytes(MutableByteArray.wrap(_bytes));
    }

    public PublicKey asPublicKey() {
        return new PublicKey(_bytes);
    }

    public String asString() {
        return StringUtil.bytesToString(_bytes); // UTF-8
    }

    @Override
    public Value asConst() {
        return this;
    }
}
