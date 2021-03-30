package com.softwareverde.bitcoin.transaction.script.signature.hashtype;

public class HashType {
    public static final Integer BYTE_COUNT = 1;

    protected static final byte ANYONE_CAN_PAY_FLAG = (byte) 0x80;
    protected static final byte BITCOIN_CASH_FLAG = (byte) 0x40;

    protected static final byte USED_BITS_MASK = (BITCOIN_CASH_FLAG | ANYONE_CAN_PAY_FLAG | Mode.BIT_MASK); // Bitmask containing the range of bits used to determine the HashType.

    public static HashType fromByte(final byte b) {
        return new HashType(b);
    }

    protected final byte _value;    // NOTE: The raw byte provided to HashType.fromByte() is needed in order to verify
                                    //  the signature, since it may have other bits set that are currently meaningless...

    protected final Mode _mode;
    protected final Boolean _shouldSignOtherInputs; // Bitcoin calls this "ANYONECANPAY" (_shouldSignOtherInputs being false indicates anyone can pay)...

    protected HashType(final byte value) {
        final boolean shouldSignOnlyOneInput = ((value & 0x80) != 0x00);
        _value = value;
        _mode = Mode.fromByte(value);
        _shouldSignOtherInputs = (! shouldSignOnlyOneInput);
    }

    public HashType(final Mode mode, final Boolean shouldSignOtherInputs, final Boolean useBitcoinCash) {
        {
            byte value = mode.getValue();
            if (! shouldSignOtherInputs) {
                value |= ANYONE_CAN_PAY_FLAG;
            }
            if (useBitcoinCash) {
                value |= BITCOIN_CASH_FLAG;
            }
            _value = value;
        }

        _mode = mode;
        _shouldSignOtherInputs = shouldSignOtherInputs;
    }

    public Mode getMode() {
        return _mode;
    }

    // NOTE: Bitcoin refers to this as "SIGHASH_FORKID"...
    public Boolean isBitcoinCashType() {
        return ((_value & BITCOIN_CASH_FLAG) != 0x00);
    }

    public Boolean shouldSignOtherInputs() {
        return _shouldSignOtherInputs;
    }

    public byte toByte() {
        return _value;
    }

    public Boolean hasUnknownFlags() {
        return ((_value & ~(HashType.USED_BITS_MASK)) != 0x00);
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