package com.softwareverde.bitcoin.secp256k1.key;

import com.softwareverde.constable.bytearray.MutableByteArray;
import org.junit.Test;

public class PublicKeyTests {
    @Test
    public void should_not_crash_with_invalid_public_key() {
        // Setup
        final PublicKey emptyPublicKey = PublicKey.fromBytes(new MutableByteArray(0));
        final PublicKey invalidPublicKey32 = PublicKey.fromBytes(new MutableByteArray(32));
        final PublicKey invalidPublicKey33 = PublicKey.fromBytes(new MutableByteArray(33));
        final PublicKey invalidPublicKey34 = PublicKey.fromBytes(new MutableByteArray(34));
        final PublicKey invalidPublicKey64 = PublicKey.fromBytes(new MutableByteArray(64));
        final PublicKey invalidPublicKey65 = PublicKey.fromBytes(new MutableByteArray(65));
        final PublicKey invalidPublicKey66 = PublicKey.fromBytes(new MutableByteArray(66));

        // Action
        emptyPublicKey.asConst();
        emptyPublicKey.isCompressed();
        emptyPublicKey.compress();
        emptyPublicKey.decompress();

        invalidPublicKey32.asConst();
        invalidPublicKey32.isCompressed();
        invalidPublicKey32.compress();
        invalidPublicKey32.decompress();

        invalidPublicKey33.asConst();
        invalidPublicKey33.isCompressed();
        invalidPublicKey33.compress();
        invalidPublicKey33.decompress();

        invalidPublicKey34.asConst();
        invalidPublicKey34.isCompressed();
        invalidPublicKey34.compress();
        invalidPublicKey34.decompress();

        invalidPublicKey64.asConst();
        invalidPublicKey64.isCompressed();
        invalidPublicKey64.compress();
        invalidPublicKey64.decompress();

        invalidPublicKey65.asConst();
        invalidPublicKey65.isCompressed();
        invalidPublicKey65.compress();
        invalidPublicKey65.decompress();

        invalidPublicKey66.asConst();
        invalidPublicKey66.isCompressed();
        invalidPublicKey66.compress();
        invalidPublicKey66.decompress();
    }
}
