package com.softwareverde.bitcoin.block.validator.difficulty;

import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.logging.Logger;

import java.math.BigInteger;

/**
 * BCH Difficulty Calculation via aserti3-2d algorithm.
 * The aserti3-2d difficulty adjustment algorithm (ASERT DAA) on Bitcoin Cash (BCH) on November 15th, 2020, as designed by Mark Lundeberg and implemented by Jonathan Toomim.
 *  https://gitlab.com/bitcoin-cash-node/bchn-sw/qa-assets/-/tree/master/test_vectors/aserti3-2d
 *  https://github.com/pokkst/bitcoincashj/blob/master/core/src/main/java/org/bitcoinj/params/AbstractBitcoinNetParams.java
 */
public class AsertDifficultyCalculator {
    public static final Long TARGET_BLOCK_SPACING = (10L * 60L); // 10 minutes per block.
    public static final Long HALF_LIFE = (2L * 24L * 60L * 60L); // 2 Days, in seconds.

    protected BigInteger _getHalfLife() {
        return BigInteger.valueOf(AsertDifficultyCalculator.HALF_LIFE);
    }

    protected Difficulty _computeAsertTarget(final AsertReferenceBlock referenceBlock, final Long previousBlockTimestamp, final BigInteger previousBlockHeight) {
        final int shiftBitCount = 16;

        final Long referenceBlockTime = referenceBlock.parentBlockTimestamp;
        final BigInteger heightDiff = previousBlockHeight.subtract(referenceBlock.blockHeight);
        final long blockTimeDifferenceInSeconds = (previousBlockTimestamp - referenceBlockTime);
        Logger.trace("anchor_bits=" + referenceBlock.difficulty.encode() + ", time_diff=" + blockTimeDifferenceInSeconds + ", height_diff=" + heightDiff);

        final BigInteger heightDifferenceWithOffset = heightDiff.add(BigInteger.ONE);
        final BigInteger desiredHeight = BigInteger.valueOf(TARGET_BLOCK_SPACING).multiply(heightDifferenceWithOffset);

        final long shiftCount;
        final BigInteger exponent;
        {
            final BigInteger halfLifeBigInteger = _getHalfLife();
            final BigInteger value = (((BigInteger.valueOf(blockTimeDifferenceInSeconds).subtract(desiredHeight)).shiftLeft(shiftBitCount)).divide(halfLifeBigInteger));
            shiftCount = (value.shiftRight(shiftBitCount)).longValue();
            exponent = value.subtract(BigInteger.valueOf(shiftCount << shiftBitCount));
        }

        final BigInteger target;
        {
            // factor = ((195766423245049 * exponent) + (971821376 * exponent^2) + (5127 * exponent^3) + 2^47) >> 48
            final BigInteger factor =
                BigInteger.valueOf(195766423245049L)
                .multiply(exponent)
                .add(BigInteger.valueOf(971821376L).multiply(exponent.pow(2)))
                .add(BigInteger.valueOf(5127L).multiply(exponent.pow(3)))
                .add(BigInteger.valueOf(2L).pow(47))
                .shiftRight(48);

            final BigInteger radix = BigInteger.ONE.shiftLeft(shiftBitCount); // 1 << shiftBitCount
            final BigInteger referenceBlockDifficulty = Difficulty.toBigInteger(referenceBlock.difficulty);
            final BigInteger unshiftedTargetDifficulty = referenceBlockDifficulty.multiply(radix.add(factor)); // referenceBlockDifficulty * (radix + factor)

            final BigInteger shiftedTargetDifficulty;
            if (shiftCount < 0) {
                shiftedTargetDifficulty = unshiftedTargetDifficulty.shiftRight(Math.abs((int) shiftCount));
            }
            else {
                shiftedTargetDifficulty = unshiftedTargetDifficulty.shiftLeft((int) shiftCount);
            }

            target = shiftedTargetDifficulty.shiftRight(shiftBitCount);
        }

        if (target.equals(BigInteger.ZERO)) {
            return Difficulty.fromBigInteger(BigInteger.ONE);
        }

        final BigInteger maxDifficulty = Difficulty.toBigInteger(Difficulty.MAX_DIFFICULTY);
        if (target.compareTo(maxDifficulty) > 0) {
            return Difficulty.MAX_DIFFICULTY;
        }

        return Difficulty.fromBigInteger(target);
    }

    public Difficulty computeAsertTarget(final AsertReferenceBlock referenceBlock, final Long previousBlockTimestamp, final Long blockHeight) {
        // For calculating the Difficulty of blockHeight N, the parent height, while technically an arbitrary choice, is used in due to the evolution of the algorithm.
        final BigInteger previousBlockHeightBigInteger = BigInteger.valueOf(blockHeight - 1L);
        return _computeAsertTarget(referenceBlock, previousBlockTimestamp, previousBlockHeightBigInteger);
    }
}
