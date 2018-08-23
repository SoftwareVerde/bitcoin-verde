package com.softwareverde.bitcoin.transaction.script.signature.hashtype;

// NOTE: Arbitrary (non-standard) HashType Modes are permitted.
//  https://bitcoin.stackexchange.com/questions/38971/op-checksig-signature-hash-type-0
public enum Mode {
    SIGNATURE_HASH_ALL((byte) 0x01),
    SIGNATURE_HASH_NONE((byte) 0x02),
    SIGNATURE_HASH_SINGLE((byte) 0x03);

    public static Mode fromByte(final byte value) {
        switch (value) {
            case 0x01: { return SIGNATURE_HASH_ALL; }
            case 0x02: { return SIGNATURE_HASH_NONE; }
            case 0x03: { return SIGNATURE_HASH_SINGLE; }
            default: { return null; }
        }
    }

    private final byte _value;

    Mode(final byte value) {
        _value = value;
    }

    public byte getValue() {
        return _value;
    }
}
