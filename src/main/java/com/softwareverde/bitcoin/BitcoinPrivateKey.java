package com.softwareverde.bitcoin;

import com.softwareverde.bitcoin.secp256k1.Secp256k1;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.util.Util;

import java.security.SecureRandom;

public class BitcoinPrivateKey {
    public static final Integer KEY_BYTE_COUNT = (256 / 8);

    public static BitcoinPrivateKey createNewKey() {
        final SecureRandom secureRandom = new SecureRandom();

        final BitcoinPrivateKey privateKey = new BitcoinPrivateKey();
        secureRandom.nextBytes(privateKey._data);
        return privateKey;
    }

    private final byte[] _data = new byte[KEY_BYTE_COUNT];

    public byte[] getBytes() {
        return Util.copyArray(_data);
    }

    public byte[] getBitcoinAddress() {
        final byte addressPrefix = 0x00;

        final byte[] publicKeyPoint = Secp256k1.getPublicKeyPoint(_data);
        final byte[] rawBitcoinAddress = BitcoinUtil.ripemd160(BitcoinUtil.sha256(publicKeyPoint));
        final byte[] encodedBitcoinAddress = BitcoinUtil.base58Check(addressPrefix, rawBitcoinAddress);

        return encodedBitcoinAddress;
    }



    @Override
    public String toString() {
        return BitcoinUtil.toHexString(_data);
    }
}
