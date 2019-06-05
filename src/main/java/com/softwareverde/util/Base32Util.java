package com.softwareverde.util;

import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;

public class Base32Util {
    protected static final String ALPHABET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l";

    /**
     * Decodes bech32string as a Base-32 encoded string, as specified here: https://github.com/bitcoin/bips/blob/master/bip-0173.mediawiki
     *  The returned ByteArray is the "compacted" byte array, where each 5-bit segments are concatenated forming a single byte array.
     */
    public static byte[] base32StringToByteArray(final String bech32String) {
        final int payloadByteCount = ((bech32String.length() * 5) / 8);
        final int payloadBitCount = (payloadByteCount * 8);
        final MutableByteArray payloadBytes = new MutableByteArray(payloadByteCount);
        int payloadBitIndex = 0;
        for (int i = 0; i < bech32String.length(); ++i) {
            final char c = bech32String.charAt(i);
            final int value = ALPHABET.indexOf(c);
            if (value < 0) { return null; }

            final ByteArray valueByteArray = MutableByteArray.wrap(new byte[]{ (byte) value });

            for (int j = 0; j < 5; ++j) {
                if (payloadBitIndex >= payloadBitCount) { break; }
                final boolean bit = valueByteArray.getBit(j + 3);
                payloadBytes.setBit(payloadBitIndex, bit);
                payloadBitIndex += 1;
            }
        }

        return payloadBytes.unwrap();
    }

    public static String toBase32String(final byte[] bytes) {
        final StringBuilder stringBuilder = new StringBuilder();

        final MutableByteArray byteArray = MutableByteArray.wrap(bytes);
        final int readBitCount = (byteArray.getByteCount() * 8);

        int readBitIndex = 0;
        while (readBitIndex < readBitCount) {
            final MutableByteArray b = new MutableByteArray(1);

            for (int i = 0; i < 5; ++i) {
                if (readBitIndex >= readBitCount) { break; }
                final boolean value = byteArray.getBit(readBitIndex);
                b.setBit((i + 3), value);
                readBitIndex += 1;
            }

            final int index = ByteUtil.byteToInteger(b.getByte(0));
            stringBuilder.append(ALPHABET.charAt(index));
        }

        return stringBuilder.toString();
    }

    protected Base32Util() { }
}
