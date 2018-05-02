package com.softwareverde.bitcoin.transaction.script.signature;

import com.softwareverde.io.Logger;
import com.softwareverde.util.HexUtil;

public class HashType {
    public enum Mode {
        SIGNATURE_ZERO(0x00),           // Not standard, but nearly identical to SIGNATURE_HASH_ALL: https://bitcoin.stackexchange.com/questions/38971/op-checksig-signature-hash-type-0
        SIGNATURE_HASH_ALL(0x01),
        SIGNATURE_HASH_NONE(0x02),
        SIGNATURE_HASH_SINGLE(0x03);

        protected final byte _value;

        Mode(final Integer value) {
            _value = (byte) value.intValue();
        }

        public static Mode fromByte(final byte b) {
            for (final Mode hashType : Mode.values()) {
                if (hashType._value == b) {
                    return hashType;
                }
            }
            Logger.log("NOTICE: Unknown HashType.Mode: " + HexUtil.toHexString(new byte[]{b}));
            return null;
        }

        public byte getValue() {
            return _value;
        }
    }

    public static HashType fromByte(final byte b) {
        final byte signBitMask = ((byte) 0x80);
        final byte hashTypeBitMask = ((byte) 0x0F);

        final Boolean shouldSignOnlyOneInput = ((b & signBitMask) == signBitMask);
        final byte hashTypeByte = (byte) (b & hashTypeBitMask);

        final Mode mode = Mode.fromByte(hashTypeByte);
        if (mode == null) { return null; }

        return new HashType(mode, (! shouldSignOnlyOneInput));
    }

    protected final Mode _mode;
    protected final Boolean _shouldSignOtherInputs;

    public HashType(final Mode mode, final Boolean shouldSignOtherInputs) {
        _mode = mode;
        _shouldSignOtherInputs = shouldSignOtherInputs;
    }

    public Mode getMode() {
        return _mode;
    }

    public Boolean shouldSignOtherInputs() {
        return _shouldSignOtherInputs;
    }

    public byte toByte() {
        final byte signBitMask = (_shouldSignOtherInputs ? (byte) 0x00 : (byte) 0x80);
        return (byte) (signBitMask | _mode.getValue());
    }

    @Override
    public int hashCode() {
        return (_mode.hashCode() + _shouldSignOtherInputs.hashCode());
    }

    @Override
    public boolean equals(final Object object) {
        if (object == null) { return false; }
        if (! (object instanceof HashType)) { return false; }
        final HashType hashType = (HashType) object;
        if (hashType._shouldSignOtherInputs != _shouldSignOtherInputs) { return false; }
        if (hashType._mode != _mode) { return false; }
        return true;
    }

    @Override
    public String toString() {
        return _mode + ":" + _shouldSignOtherInputs;
    }
}