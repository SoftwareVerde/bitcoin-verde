package com.softwareverde.bitcoin.type.address;

import com.softwareverde.bitcoin.type.key.PrivateKey;
import com.softwareverde.bitcoin.type.key.PublicKey;
import com.softwareverde.bitcoin.util.BitcoinUtil;

public class CompressedAddress extends Address {
    public static CompressedAddress fromPrivateKey(final PrivateKey privateKey) {
        final PublicKey compressedPublicKeyPoint = privateKey.getCompressedPublicKey();

        final byte[] rawBitcoinAddress = BitcoinUtil.ripemd160(BitcoinUtil.sha256(compressedPublicKeyPoint.getBytes()));
        return new CompressedAddress(rawBitcoinAddress);
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
