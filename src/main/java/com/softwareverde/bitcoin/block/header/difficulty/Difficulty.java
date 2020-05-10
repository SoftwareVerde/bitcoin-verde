package com.softwareverde.bitcoin.block.header.difficulty;

import com.softwareverde.bitcoin.block.header.difficulty.work.BlockWork;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.Constable;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.ImmutableByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.util.HexUtil;

import java.math.BigDecimal;
import java.math.BigInteger;

public interface Difficulty extends Constable<ImmutableDifficulty> {
    Integer BASE_DIFFICULTY_EXPONENT = (0x1D - 0x03);
    ByteArray BASE_DIFFICULTY_SIGNIFICAND = new ImmutableByteArray(new byte[] { (byte) 0x00, (byte) 0xFF, (byte) 0xFF });
    ImmutableDifficulty BASE_DIFFICULTY = new ImmutableDifficulty(BASE_DIFFICULTY_SIGNIFICAND, BASE_DIFFICULTY_EXPONENT);

    Long MAX_SIGNIFICAND_VALUE = ByteUtil.bytesToLong(HexUtil.hexStringToByteArray("7FFFFF"));

    static ImmutableDifficulty fromBigInteger(final BigInteger bigInteger) {
        final int significandByteCount = 3;

        final byte[] bytes = bigInteger.toByteArray();
        final int exponent = (bytes.length - significandByteCount);
        final ByteArray significand = MutableByteArray.wrap(ByteUtil.copyBytes(bytes, 0, significandByteCount));
        // Since significand is normally* interpreted as a signed value, its max value is 0x7FFFFF.
        // If significand is greater than this value, then shift the significand right one, and increase the exponent.
        //  * Why it's considered signed seems unjustified. So it goes.

        // HexUtil.toHexString(Difficulty.BASE_DIFFICULTY._toBigDecimal().toBigInteger().toByteArray()) returns:
        //           00FFFF0000000000000000000000000000000000000000000000000000

        // HexUtil.toHexString(Difficulty.BASE_DIFFICULTY._convertToBytes()) returns:
        //     00000000FFFF0000000000000000000000000000000000000000000000000000

        // HexUtil.toHexString(Difficulty.BASE_DIFFICULTY._toBigInteger().toByteArray()) returns:
        //           00FFFF0000000000000000000000000000000000000000000000000000

        if (ByteUtil.bytesToLong(significand) >= MAX_SIGNIFICAND_VALUE) {
            // Shifting the value will lose precision.
            //  The value will go from:
            //     00000000FFFF0000000000000000000000000000000000000000000000000000 (1D00FFFF)
            //  To:
            //     00000000FF000000000000000000000000000000000000000000000000000000 (1E0000FF)
            final byte[] shiftedSignificand = new byte[] { 0x00, significand.getByte(0), significand.getByte(1) };
            final int shiftedExponent = (exponent + 1);
            return new ImmutableDifficulty(MutableByteArray.wrap(shiftedSignificand), shiftedExponent);
        }

        return new ImmutableDifficulty(significand, exponent);
    }

    static BigDecimal calculateHashesPerSecond(final Difficulty difficulty) {
        return difficulty.getDifficultyRatio().multiply(BigDecimal.valueOf(1L << 32).divide(BigDecimal.valueOf(600L), BigDecimal.ROUND_HALF_UP));
    }

    static ImmutableDifficulty decode(final ByteArray encodedBytes) {
        if (encodedBytes == null) { return null; }
        if (encodedBytes.getByteCount() != 4) { return null; }
        final ByteArray significand = MutableByteArray.wrap(ByteUtil.copyBytes(encodedBytes, 1, 3));
        final Integer exponent = (ByteUtil.byteToInteger(encodedBytes.getByte(0)) - 3);
        return new ImmutableDifficulty(significand, exponent);
    }

    Integer getExponent();
    ByteArray getSignificand();

    Boolean isSatisfiedBy(final Sha256Hash hash);
    Boolean isLessDifficultThan(final Difficulty difficulty);
    BigDecimal getDifficultyRatio();

    Difficulty multiplyBy(final double difficultyAdjustment);
    Difficulty divideBy(final double difficultyAdjustment);

    BlockWork calculateWork();

    ByteArray getBytes();
    ByteArray encode();

    @Override
    ImmutableDifficulty asConst();
}
