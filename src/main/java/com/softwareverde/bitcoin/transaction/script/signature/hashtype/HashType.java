package com.softwareverde.bitcoin.transaction.script.signature.hashtype;

public class HashType {
    public static HashType fromByte(final byte b) {
        final byte signBitMask = ((byte) 0x80);
        final byte hashTypeBitMask = ((byte) 0x0F);

        final Boolean shouldSignOnlyOneInput = ((b & signBitMask) == signBitMask);
        final byte hashTypeByte = (byte) (b & hashTypeBitMask);

        final Mode mode = Mode.fromByte(hashTypeByte);
        return new HashType(mode, (! shouldSignOnlyOneInput));
    }

    protected final Mode _mode;
    protected final Boolean _shouldSignOtherInputs; // Bitcoin calls this "ANYONECANPAY" (false indicating anyone can pay)...

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
        if (! hashType._mode.equals(_mode)) { return false; }
        return true;
    }

    @Override
    public String toString() {
        return _mode + ":" + _shouldSignOtherInputs;
    }
}