package com.softwareverde.bitcoin.address;

public class PayToScriptHashAddress extends Address {
    public static final byte PREFIX = (byte) 0x05;
    public static final byte BASE_32_PREFIX = (byte) (0x08);

    @Override
    protected byte _getPrefix() {
        return PREFIX;
    }

    @Override
    protected byte _getBase32Prefix() { return BASE_32_PREFIX; }

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
