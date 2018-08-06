package com.softwareverde.bitcoin.transaction.script.signature.hashtype;

import com.softwareverde.util.HexUtil;

public class Mode {
    // NOTE: Comparisons may be done with "==" due to the static method returning these singletons.
    //  Mode _should_ have been an enum but it cannot since arbitrary (non-standard) HashType Modes are permitted.
    //  https://bitcoin.stackexchange.com/questions/38971/op-checksig-signature-hash-type-0
    public static final Mode SIGNATURE_HASH_ALL = new Mode((byte) 0x01);
    public static final Mode SIGNATURE_HASH_NONE = new Mode((byte) 0x02);
    public static final Mode SIGNATURE_HASH_SINGLE = new Mode((byte) 0x03);

    public static Mode fromByte(final byte b) {
        switch (b) {
            case 0x01: { return SIGNATURE_HASH_ALL; }
            case 0x02: { return SIGNATURE_HASH_NONE; }
            case 0x03: { return SIGNATURE_HASH_SINGLE; }
            default: { return new Mode(b); }
        }
    }

    protected final byte _value;

    private Mode(final byte value) {
        _value = value;
    }

    public byte getValue() {
        return _value;
    }

    @Override
    public boolean equals(final Object object) {
        if (object == null) { return false; }
        if (! (object instanceof Mode)) { return false; }

        final Mode mode = (Mode) object;
        return (_value == mode._value);
    }

    @Override
    public int hashCode() {
        return Byte.hashCode(_value);
    }

    @Override
    public String toString() {
        return HexUtil.toHexString(new byte[] { _value });
    }
}