package com.softwareverde.bitcoin.type.address;

public class PayToScriptHashAddress extends Address {
    public static final byte PREFIX = (byte) 0x05;

    @Override
    public byte _getPrefix() {
        return PREFIX;
    }

    protected PayToScriptHashAddress(final byte[] bytes) {
        super(bytes);
    }

    @Override
    public Boolean isCompressed() { return true; }

    @Override
    public PayToScriptHashAddress asConst() {
        return this;
    }
}
