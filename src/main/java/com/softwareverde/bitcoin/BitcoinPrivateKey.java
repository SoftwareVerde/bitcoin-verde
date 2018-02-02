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
        return BitcoinUtil.calculateChecksummedKey(addressPrefix, rawBitcoinAddress);
    }

    public byte[] getCompressedBitcoinAddress() {
        final byte addressPrefix = 0x00;

        final byte[] publicKeyPoint = Secp256k1.getPublicKeyPoint(_data);
        final Integer coordinateByteCount = ((publicKeyPoint.length - 1) / 2);

        final Integer prefixByteCount = 1;
        final byte prefix = publicKeyPoint[0];
        final byte[] publicKeyPointX = new byte[coordinateByteCount];
        final byte[] publicKeyPointY = new byte[coordinateByteCount];
        {
            for (int i=0; i<coordinateByteCount; ++i) {
                publicKeyPointX[i] = publicKeyPoint[prefixByteCount + i];
                publicKeyPointY[i] = publicKeyPoint[prefixByteCount + coordinateByteCount + i];
            }
        }
        final Boolean yCoordinateIsEven = ((publicKeyPointY[coordinateByteCount - 1] & 0xFF) % 2 == 0);
        final byte compressedPublicKeyPrefix = (yCoordinateIsEven ? (byte) 0x02 : (byte) 0x03);
        final byte[] compressedPublicKeyPoint = new byte[coordinateByteCount + prefixByteCount];
        {
            compressedPublicKeyPoint[0] = compressedPublicKeyPrefix;
            for (int i=0; i<publicKeyPointX.length; ++i) {
                compressedPublicKeyPoint[prefixByteCount + i] = publicKeyPointX[i];
            }
        }

        final byte[] rawBitcoinAddress = BitcoinUtil.ripemd160(BitcoinUtil.sha256(compressedPublicKeyPoint));
        return BitcoinUtil.calculateChecksummedKey(addressPrefix, rawBitcoinAddress);
    }

    @Override
    public String toString() {
        return BitcoinUtil.toHexString(_data);
    }
}
