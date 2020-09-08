package com.softwareverde.bitcoin.block.validator.difficulty;

import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;

import java.math.BigInteger;

public class AsertDifficultyCalculator {
    public static final BigInteger TARGET_SPACING = BigInteger.valueOf(10L * 60L);  // 10 minutes per block.
    public static final Long HALF_LIFE = 2L * 24L * 60L * 60L; // 2 Days, in seconds.

    protected static BigInteger MAX_DIFFICULTY_BIG_INTEGER = Difficulty.toBigInteger(Difficulty.MAX_DIFFICULTY);

    public static Difficulty computeAsertTarget(final BigInteger refTarget, final BigInteger referenceBlockAncestorTime, final BigInteger referenceBlockHeight, final BigInteger evalBlockTime, final BigInteger evalBlockHeight) {
        final BigInteger heightDiff = evalBlockHeight.subtract(referenceBlockHeight);
        final BigInteger timeDiff = evalBlockTime.subtract(referenceBlockAncestorTime);
        final BigInteger halfLife = BigInteger.valueOf(HALF_LIFE);
        final BigInteger rBits = BigInteger.valueOf(16L);
        final BigInteger radix = BigInteger.ONE.shiftLeft(rBits.intValue());

        final BigInteger heightDiffWithOffset = heightDiff.add(BigInteger.ONE);
        final BigInteger targetHeightOffsetMultiple = TARGET_SPACING.multiply(heightDiffWithOffset);

        final BigInteger numShifts;
        final BigInteger exponent;
        {
            final BigInteger value = timeDiff
                .subtract(targetHeightOffsetMultiple)
                .shiftLeft(rBits.intValue())
                .divide(halfLife);
            numShifts = value.shiftRight(rBits.intValue());
            exponent = value.subtract(numShifts.shiftLeft(rBits.intValue()));
        }

        final BigInteger target;
        {
            final BigInteger factor =
                BigInteger.valueOf(195766423245049L)
                .multiply(exponent)
                .add(
                    BigInteger.valueOf(971821376L)
                    .multiply(exponent.pow(2))
                )
                .add(
                    BigInteger.valueOf(5127L)
                        .multiply(exponent.pow(3))
                )
                .add(
                    BigInteger.valueOf(2L)
                        .pow(47)
                )
                .shiftRight(48);

            final BigInteger unshiftedTarget = refTarget.multiply(
                radix.add(factor)
            );

            final BigInteger shiftedTarget;
            if (numShifts.compareTo(BigInteger.ZERO) < 0) {
                shiftedTarget = unshiftedTarget.shiftRight(-numShifts.intValue());
            }
            else {
                shiftedTarget = unshiftedTarget.shiftLeft(numShifts.intValue());
            }

            target = shiftedTarget.shiftRight(16);
        }

        if (target.equals(BigInteger.ZERO)) {
            return Difficulty.fromBigInteger(BigInteger.ONE);
        }

        if (target.compareTo(MAX_DIFFICULTY_BIG_INTEGER) > 0) {
            return Difficulty.MAX_DIFFICULTY;
        }

        return Difficulty.fromBigInteger(target);
    }
}
