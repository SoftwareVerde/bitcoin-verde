package com.softwareverde.bitcoin.address;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.ImmutableByteArray;
import com.softwareverde.cryptography.hash.ripemd160.Ripemd160Hash;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public class Address extends ImmutableByteArray {
    public static final Integer BYTE_COUNT_RIPE_MD = Ripemd160Hash.BYTE_COUNT;
    public static final Integer BYTE_COUNT_SHA_256 = Sha256Hash.BYTE_COUNT;

    public static Boolean isValidByteCount(final ByteArray byteArray) {
        if (byteArray == null) { return false; }
        final int byteCount = byteArray.getByteCount();
        return (byteCount == BYTE_COUNT_RIPE_MD || byteCount == BYTE_COUNT_SHA_256);
    }

    protected Address(final byte[] bytes) {
        super(bytes);
    }

    protected Address(final ByteArray bytes) {
        super(bytes);
    }

    @Override
    public Address asConst() {
        return this;
    }
}
