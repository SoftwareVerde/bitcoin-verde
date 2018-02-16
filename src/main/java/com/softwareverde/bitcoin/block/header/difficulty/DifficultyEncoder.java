package com.softwareverde.bitcoin.block.header.difficulty;

import com.softwareverde.bitcoin.util.ByteUtil;

public class DifficultyEncoder {
    public Long decodeDifficulty(final byte[] bytes) {
        if (bytes.length != 4) { return null; }

        final int exponent = (ByteUtil.byteToInteger(bytes[0]) - 3);

        final long significand;
        {
            final byte[] significandBytes = new byte[3];
            significandBytes[0] = bytes[1];
            significandBytes[1] = bytes[2];
            significandBytes[2] = bytes[3];
            significand = ByteUtil.bytesToInteger(significandBytes);
        }

        if (exponent > 0) {
            return significand << (8 * exponent);
        }
        else {
            return significand >>> (8 * Math.abs(exponent));
        }
    }

    public byte[] encodeDifficulty(final Long difficulty) {
        int exponent;
        {
            int shiftCount = 0;
            long tempDifficulty = difficulty;
            while ((tempDifficulty = (tempDifficulty >> 8)) != 0) {
                shiftCount += 1;
            }
            exponent = (shiftCount + 1);
        }

        long mantissa;
        if (exponent <= 3) {
            mantissa = difficulty << 8 * (3 - exponent);
        }
        else {
            mantissa = difficulty;
            final int shiftRightCount = (exponent - 3);
            for (int i=0; i<shiftRightCount; ++i) {
                mantissa = (mantissa >> 8);
            }
        }

        if ((mantissa & 0x00800000L) != 0) {
            mantissa >>= 8;
            exponent += 1;
        }

        final int encodedDifficulty = (int) ((mantissa) | (exponent << 24));
        return ByteUtil.integerToBytes(encodedDifficulty);
    }
}
