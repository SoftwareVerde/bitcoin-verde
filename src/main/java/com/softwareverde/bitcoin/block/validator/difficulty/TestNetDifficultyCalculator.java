package com.softwareverde.bitcoin.block.validator.difficulty;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.context.DifficultyCalculatorContext;
import com.softwareverde.bitcoin.util.Util;

public class TestNetDifficultyCalculator extends DifficultyCalculator {
    protected static final Long TWENTY_MINUTES = (20L * 60L);

    protected Long _getSecondsElapsed(final Long blockHeight) {
        final BlockHeader previousBlockHeader = _context.getBlockHeader(blockHeight - 1L);
        final BlockHeader blockHeader = _context.getBlockHeader(blockHeight);
        return (blockHeader.getTimestamp() - previousBlockHeader.getTimestamp());
    }

    protected TestNetDifficultyCalculator(final DifficultyCalculatorContext blockchainContext, final MedianBlockHeaderSelector medianBlockHeaderSelector) {
        super(blockchainContext, medianBlockHeaderSelector);
    }

    public TestNetDifficultyCalculator(final DifficultyCalculatorContext blockchainContext) {
        super(blockchainContext);
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
    protected Difficulty _calculateAserti32dBitcoinCashTarget(final Long blockHeight, final MedianBlockTime medianBlockTime) {
        final Long secondsElapsed = _getSecondsElapsed(blockHeight);
        if ((secondsElapsed > TWENTY_MINUTES) ) {
            return Difficulty.BASE_DIFFICULTY;
        }

        return super._calculateAserti32dBitcoinCashTarget(blockHeight, medianBlockTime);
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
