package com.softwareverde.bitcoin.address;

public class CompressedAddress extends Address {
    public static final byte PREFIX = (byte) 0x00;

    @Override
    public byte _getPrefix() {
        return PREFIX;
    }

    protected CompressedAddress(final byte[] bytes) {
        super(bytes);
    }

    @Override
    public Boolean isCompressed() { return true; }

    @Override
    public CompressedAddress asConst() {
        return this;
    }
}
