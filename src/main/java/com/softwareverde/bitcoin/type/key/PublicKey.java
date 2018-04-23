package com.softwareverde.bitcoin.type.key;

import com.softwareverde.bitcoin.secp256k1.Secp256k1;
import com.softwareverde.constable.Const;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.ImmutableByteArray;

public class PublicKey extends ImmutableByteArray implements Const {
    protected Boolean _isCompressed() {
        final byte firstByte = _bytes[0];
        return ( (firstByte == (byte) 0x02) || (firstByte == (byte) 0x03) );
    }

    public PublicKey(final byte[] bytes) {
        super(bytes);
    }

    public PublicKey(final ByteArray byteArray) {
        super(byteArray);
    }

    public Boolean isCompressed() {
        return _isCompressed();
    }

    public PublicKey decompress() {
        if (! _isCompressed()) { return this; }

        final byte[] decompressedBytes = Secp256k1.decompressPoint(_bytes);
        decompressedBytes[0] = (byte) 0x04;
        return new PublicKey(decompressedBytes);
    }

    @Override
    public PublicKey asConst() {
        return this;
    }
}
