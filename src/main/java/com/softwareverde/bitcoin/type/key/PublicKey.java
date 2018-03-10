package com.softwareverde.bitcoin.type.key;

import com.softwareverde.bitcoin.type.bytearray.ByteArray;
import com.softwareverde.bitcoin.type.bytearray.ImmutableByteArray;

public class PublicKey extends ImmutableByteArray {
    public PublicKey(final byte[] bytes) {
        super(bytes);
    }

    public PublicKey(final ByteArray byteArray) {
        super(byteArray);
    }

    @Override
    public PublicKey asConst() {
        return this;
    }
}
