package com.softwareverde.bitcoin.block.validator.difficulty;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.server.module.node.Blockchain;
import com.softwareverde.util.Util;
import com.softwareverde.util.type.time.SystemTime;

public class TestNetDifficultyCalculator extends DifficultyCalculator {
    protected final SystemTime _systemTime = new SystemTime();

    protected static final Long TWENTY_MINUTES = (20L * 60L);
    protected static final Long TEST_NET_ASERT_HALF_LIFE = (60L * 60L);

    protected Long _getSecondsElapsed(final Long blockHeight) {
        final Long previousBlockHeight = (blockHeight - 1L);
        final BlockHeader previousBlockHeader = _blockchain.getBlockHeader(previousBlockHeight);
        final BlockHeader blockHeader = _blockchain.getBlockHeader(blockHeight);

        final Long blockHeaderTimestamp;
        if (blockHeader != null) {
            blockHeaderTimestamp = blockHeader.getTimestamp();
        }
        else {
            final MedianBlockTime medianTimePast = _blockchain.getMedianBlockTime(previousBlockHeight);
            final Long medianTimePastInSeconds = medianTimePast.getCurrentTimeInSeconds();
            final Long currentTime = _systemTime.getCurrentTimeInSeconds();
            blockHeaderTimestamp = Math.max((medianTimePastInSeconds + 1L), currentTime);
        }

        final Long previousBlockHeaderTimestamp = previousBlockHeader.getTimestamp();
        return (blockHeaderTimestamp - previousBlockHeaderTimestamp);
    }

    public TestNetDifficultyCalculator(final Blockchain blockchain, final UpgradeSchedule upgradeSchedule) {
        super(blockchain, upgradeSchedule);
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

            final BlockHeader blockHeader = _blockchain.getBlockHeader(ancestorBlockHeight);
            final Difficulty difficulty = blockHeader.getDifficulty();
            final boolean requiresDifficultyEvaluation = (ancestorBlockHeight % BLOCK_COUNT_PER_DIFFICULTY_ADJUSTMENT == 0);
            if (requiresDifficultyEvaluation || (! Util.areEqual(difficulty, Difficulty.BASE_DIFFICULTY))) {
                return difficulty;
            }

            ancestorBlockHeight -= 1L;
        }
    }
}
