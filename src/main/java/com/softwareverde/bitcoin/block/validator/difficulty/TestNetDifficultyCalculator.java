package com.softwareverde.bitcoin.block.validator.difficulty;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.context.DifficultyCalculatorContext;
import com.softwareverde.util.Util;
import com.softwareverde.util.type.time.SystemTime;

import java.math.BigInteger;

public class TestNetDifficultyCalculator extends DifficultyCalculator {
    protected final SystemTime _systemTime = new SystemTime();

    protected static final Long TWENTY_MINUTES = (20L * 60L);
    protected static final Long TEST_NET_ASERT_HALF_LIFE = (60L * 60L);

    protected Long _getSecondsElapsed(final Long blockHeight) {
        final Long previousBlockHeight = (blockHeight - 1L);
        final BlockHeader previousBlockHeader = _context.getBlockHeader(previousBlockHeight);
        final BlockHeader blockHeader = _context.getBlockHeader(blockHeight);

        final Long blockHeaderTimestamp;
        if (blockHeader != null) {
            blockHeaderTimestamp = blockHeader.getTimestamp();
        }
        else {
            final MedianBlockTime medianTimePast = _context.getMedianBlockTime(previousBlockHeight);
            final Long medianTimePastInSeconds = medianTimePast.getCurrentTimeInSeconds();
            final Long currentTime = _systemTime.getCurrentTimeInSeconds();
            blockHeaderTimestamp = Math.max((medianTimePastInSeconds + 1L), currentTime);
        }

        final Long previousBlockHeaderTimestamp = previousBlockHeader.getTimestamp();
        return (blockHeaderTimestamp - previousBlockHeaderTimestamp);
    }

    protected TestNetDifficultyCalculator(final DifficultyCalculatorContext blockchainContext, final MedianBlockHeaderSelector medianBlockHeaderSelector, final AsertDifficultyCalculator asertDifficultyCalculator) {
        super(blockchainContext, medianBlockHeaderSelector, asertDifficultyCalculator);
    }

    public TestNetDifficultyCalculator(final DifficultyCalculatorContext blockchainContext) {
        this(blockchainContext, new MedianBlockHeaderSelector(), new AsertDifficultyCalculator() {
            @Override
            protected BigInteger _getHalfLife() {
                return BigInteger.valueOf(TEST_NET_ASERT_HALF_LIFE);
            }
        });
    }

    @Override
    protected Difficulty _calculateNewBitcoinCoreTarget(final Long forBlockHeight) {
        // Intentionally does not abide by the 20 minute bypass...
        return super._calculateNewBitcoinCoreTarget(forBlockHeight);
    }

    @Override
    protected Difficulty _calculateBitcoinCashEmergencyDifficultyAdjustment(final Long forBlockHeight) {
        final Long secondsElapsed = _getSecondsElapsed(forBlockHeight);
        if ((secondsElapsed > TWENTY_MINUTES) ) {
            return Difficulty.BASE_DIFFICULTY;
        }

        return super._calculateBitcoinCashEmergencyDifficultyAdjustment(forBlockHeight);
    }

    @Override
    protected Difficulty _calculateAserti32dBitcoinCashTarget(final Long blockHeight) {
        final Long secondsElapsed = _getSecondsElapsed(blockHeight);
        if ((secondsElapsed > TWENTY_MINUTES) ) {
            return Difficulty.BASE_DIFFICULTY;
        }

        return super._calculateAserti32dBitcoinCashTarget(blockHeight);
    }

    @Override
    protected Difficulty _calculateCw144BitcoinCashTarget(final Long forBlockHeight) {
        final Long secondsElapsed = _getSecondsElapsed(forBlockHeight);
        if ((secondsElapsed > TWENTY_MINUTES) ) {
            return Difficulty.BASE_DIFFICULTY;
        }

        return super._calculateCw144BitcoinCashTarget(forBlockHeight);
    }

    @Override
    protected Difficulty _getParentDifficulty(final Long blockHeight) {
        final Long secondsElapsed = _getSecondsElapsed(blockHeight);
        if ((secondsElapsed > TWENTY_MINUTES) ) {
            return Difficulty.BASE_DIFFICULTY;
        }

        long ancestorBlockHeight = (blockHeight - 1L);
        while (true) {
            if (ancestorBlockHeight < 1) {
                return Difficulty.BASE_DIFFICULTY;
            }

            final BlockHeader blockHeader = _context.getBlockHeader(ancestorBlockHeight);
            final Difficulty difficulty = blockHeader.getDifficulty();
            final boolean requiresDifficultyEvaluation = (ancestorBlockHeight % BLOCK_COUNT_PER_DIFFICULTY_ADJUSTMENT == 0);
            if (requiresDifficultyEvaluation || (! Util.areEqual(difficulty, Difficulty.BASE_DIFFICULTY))) {
                return difficulty;
            }

            ancestorBlockHeight -= 1L;
        }
    }
}
