package com.softwareverde.bitcoin.block.validator.difficulty;

import com.softwareverde.bitcoin.bip.Bip55;
import com.softwareverde.bitcoin.bip.HF20171113;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.header.difficulty.ImmutableDifficulty;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.io.Logger;
import com.softwareverde.util.DateUtil;
import com.softwareverde.util.Util;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Comparator;

public class DifficultyCalculator {
    public static Boolean LOGGING_ENABLED = false;

    protected static final Integer BLOCK_COUNT_PER_DIFFICULTY_ADJUSTMENT = 2016;
    protected static final BigInteger TWO_RAISED_TO_256 = BigInteger.valueOf(2L).pow(256);

    protected final BlockDatabaseManager _blockDatabaseManager;

    public DifficultyCalculator(final MysqlDatabaseConnection databaseConnection, final DatabaseManagerCache databaseManagerCache) {
        _blockDatabaseManager = new BlockDatabaseManager(databaseConnection, databaseManagerCache);
    }

    protected Difficulty _calculateNewBitcoinCoreTarget(final BlockChainSegmentId blockChainSegmentId, final Long blockHeight, final BlockHeader blockHeader) throws DatabaseException {
        //  Calculate the new difficulty. https://bitcoin.stackexchange.com/questions/5838/how-is-difficulty-calculated

        //  1. Get the block that is 2016 blocks behind the head block of this chain.
        final long previousBlockHeight = (blockHeight - BLOCK_COUNT_PER_DIFFICULTY_ADJUSTMENT); // NOTE: This is 2015 blocks worth of time (not 2016) because of a bug in Satoshi's implementation and is now part of the protocol definition.
        final BlockId blockId = _blockDatabaseManager.getBlockIdAtHeight(blockChainSegmentId, previousBlockHeight);
        final BlockHeader blockWithPreviousAdjustment = _blockDatabaseManager.getBlockHeader(blockId);
        if (blockWithPreviousAdjustment == null) { return null; }

        //  2. Get the current block timestamp.
        final BlockHeader previousBlock;
        final Long blockTimestamp;
        {
            final BlockId previousBlockId = _blockDatabaseManager.getBlockIdFromHash(blockHeader.getPreviousBlockHash());
            previousBlock = _blockDatabaseManager.getBlockHeader(previousBlockId);
            blockTimestamp = previousBlock.getTimestamp();
        }
        final Long previousBlockTimestamp = blockWithPreviousAdjustment.getTimestamp();

        if (LOGGING_ENABLED) {
            Logger.log(DateUtil.Utc.timestampToDatetimeString(blockTimestamp * 1000L));
            Logger.log(DateUtil.Utc.timestampToDatetimeString(previousBlockTimestamp * 1000L));
        }

        //  3. Calculate the difference between the network-time and the time of the 2015th-parent block ("secondsElapsed"). (NOTE: 2015 instead of 2016 due to protocol bug.)
        final Long secondsElapsed = (blockTimestamp - previousBlockTimestamp);
        if (LOGGING_ENABLED) {
            Logger.log("2016 blocks in " + secondsElapsed + " (" + (secondsElapsed / 60F / 60F / 24F) + " days)");
        }

        //  4. Calculate the desired two-weeks elapse-time ("secondsInTwoWeeks").
        final Long secondsInTwoWeeks = 2L * 7L * 24L * 60L * 60L; // <Week Count> * <Days / Week> * <Hours / Day> * <Minutes / Hour> * <Seconds / Minute>

        //  5. Calculate the difficulty adjustment via (secondsInTwoWeeks / secondsElapsed) ("difficultyAdjustment").
        final double difficultyAdjustment = (secondsInTwoWeeks.doubleValue() / secondsElapsed.doubleValue());
        if (LOGGING_ENABLED) {
            Logger.log("Adjustment: " + difficultyAdjustment);
        }

        //  6. Bound difficultyAdjustment between [4, 0.25].
        final double boundedDifficultyAdjustment = (Math.min(4D, Math.max(0.25D, difficultyAdjustment)));

        //  7. Multiply the difficulty by the bounded difficultyAdjustment.
        final Difficulty newDifficulty = (previousBlock.getDifficulty().multiplyBy(1.0D / boundedDifficultyAdjustment));

        //  8. The new difficulty cannot be less than the base difficulty.
        final Difficulty minimumDifficulty = Difficulty.BASE_DIFFICULTY;
        if (newDifficulty.isLessDifficultThan(minimumDifficulty)) {
            return minimumDifficulty;
        }

        return newDifficulty;
    }

    protected Difficulty _calculateBitcoinCashEmergencyDifficultyAdjustment(final BlockId blockId, final Long blockHeight, final BlockHeader blockHeader) throws DatabaseException {
        final BlockId previousBlockBlockId = _blockDatabaseManager.getBlockIdFromHash(blockHeader.getPreviousBlockHash());
        if (previousBlockBlockId == null) { return null; }

        final BlockHeader previousBlockHeader = _blockDatabaseManager.getBlockHeader(previousBlockBlockId);

        final MedianBlockTime medianBlockTime = _blockDatabaseManager.calculateMedianBlockTime(blockId);
        final BlockId sixthParentBlockId = _blockDatabaseManager.getAncestorBlockId(blockId, 6);
        final MedianBlockTime medianBlockTimeForSixthBlock = _blockDatabaseManager.calculateMedianBlockTime(sixthParentBlockId);
        final Long secondsInTwelveHours = 43200L;

        if (medianBlockTime == null || medianBlockTimeForSixthBlock == null) {
            Logger.log("Unable to calculate difficulty for block: " + blockHeader.getHash());
            return null;
        }

        if (medianBlockTime.getCurrentTimeInSeconds() - medianBlockTimeForSixthBlock.getCurrentTimeInSeconds() > secondsInTwelveHours) {
            final Difficulty newDifficulty = previousBlockHeader.getDifficulty().multiplyBy(1.25D);

            final Difficulty minimumDifficulty = Difficulty.BASE_DIFFICULTY;
            if (newDifficulty.isLessDifficultThan(minimumDifficulty)) {
                return minimumDifficulty;
            }

            if (LOGGING_ENABLED) {
                Logger.log("Emergency Difficulty Adjustment: BlockHeight: " + blockHeight + " Original Difficulty: " + previousBlockHeader.getDifficulty() + " New Difficulty: " + newDifficulty);
            }
            return newDifficulty;
        }

        return previousBlockHeader.getDifficulty();
    }

    protected Difficulty _calculateNewBitcoinCashTarget(final BlockChainSegmentId blockChainSegmentId, final BlockId blockId, final Long blockHeight) throws DatabaseException {
        final BlockHeader[] lastBlockHeaders = new BlockHeader[3];
        final BlockHeader[] firstBlockHeaders = new BlockHeader[3];

        for (int i = 0; i < lastBlockHeaders.length; ++i) {
            final BlockId ancestorBlockId = _blockDatabaseManager.getAncestorBlockId(blockId, (i + 1));
            final BlockHeader blockHeader = _blockDatabaseManager.getBlockHeader(ancestorBlockId);
            if (blockHeader == null) { return null; }

            lastBlockHeaders[i] = blockHeader;
        }

        for (int i = 0; i < firstBlockHeaders.length; ++i) {
            final Long parentBlockHeight = (blockHeight - 1);
            final BlockId blockHeaderId = _blockDatabaseManager.getBlockIdAtHeight(blockChainSegmentId, (parentBlockHeight - 144L - i));
            if (blockHeaderId == null) { return null; }

            final BlockHeader blockHeader = _blockDatabaseManager.getBlockHeader(blockHeaderId);
            firstBlockHeaders[i] = blockHeader;
        }

        final Comparator<BlockHeader> sortBlockHeaderByTimestampDescending = new Comparator<BlockHeader>() {
            @Override
            public int compare(final BlockHeader o1, final BlockHeader o2) {
                return (o2.getTimestamp().compareTo(o1.getTimestamp()));
            }
        };

        Arrays.sort(lastBlockHeaders, sortBlockHeaderByTimestampDescending);
        Arrays.sort(firstBlockHeaders, sortBlockHeaderByTimestampDescending);

        final BlockHeader firstBlockHeader = firstBlockHeaders[1];
        final BlockHeader lastBlockHeader = lastBlockHeaders[1];

        final Long timeSpan;
        {
            final Long minimumValue = (72L * 600L);
            final Long maximumValue = (288L * 600L);
            final Long difference = (lastBlockHeader.getTimestamp() - firstBlockHeader.getTimestamp());

            if (difference < minimumValue) {
                timeSpan = minimumValue;
            }
            else if (difference > maximumValue) {
                timeSpan = maximumValue;
            }
            else {
                timeSpan = difference;
            }
        }

        final BlockId firstBlockId = _blockDatabaseManager.getBlockIdFromHash(firstBlockHeader.getHash());
        final BlockId lastBlockId = _blockDatabaseManager.getBlockIdFromHash(lastBlockHeader.getHash());
        final ChainWork firstChainWork = _blockDatabaseManager.getChainWork(firstBlockId);
        final ChainWork lastChainWork = _blockDatabaseManager.getChainWork(lastBlockId);

        final BigInteger workPerformed;
        {
            final BigInteger firstChainWorkBigInteger = new BigInteger(firstChainWork.getBytes());
            final BigInteger lastChainWorkBigInteger = new BigInteger(lastChainWork.getBytes());
            workPerformed = lastChainWorkBigInteger.subtract(firstChainWorkBigInteger);
        }

        final BigInteger projectedWork;
        {
            projectedWork = workPerformed
                .multiply(BigInteger.valueOf(600L))
                .divide(BigInteger.valueOf(timeSpan));
        }

        final BigInteger targetWork;
        {
            targetWork = TWO_RAISED_TO_256
                .subtract(projectedWork)
                .divide(projectedWork);
        }

        final Difficulty newDifficulty = ImmutableDifficulty.fromBigInteger(targetWork);

        final Difficulty minimumDifficulty = Difficulty.BASE_DIFFICULTY;
        if (newDifficulty.isLessDifficultThan(minimumDifficulty)) {
            return minimumDifficulty;
        }

        return newDifficulty;
    }

    public Difficulty calculateRequiredDifficulty(final BlockChainSegmentId blockChainSegmentId, final BlockHeader blockHeader) {
        try {
            final BlockId blockId = _blockDatabaseManager.getBlockIdFromHash(blockHeader.getHash());
            if (blockId == null) {
                Logger.log("Unable to find BlockId from Hash: "+ blockHeader.getHash());
                return null;
            }

            final Long blockHeight = _blockDatabaseManager.getBlockHeightForBlockId(blockId); // blockChainSegment.getBlockHeight();  // NOTE: blockChainSegment.getBlockHeight() is not safe when replaying block-validation.
            if (blockHeight == null) {
                Logger.log("Invalid BlockHeight for BlockId: "+ blockId);
                return null;
            }

            final Boolean isFirstBlock = (Util.areEqual(blockHeader.getHash(), BlockHeader.GENESIS_BLOCK_HASH)); // (blockChainSegment.getBlockHeight() == 0);
            if (isFirstBlock) { return Difficulty.BASE_DIFFICULTY; }

            if (HF20171113.isEnabled(blockHeight)) {
                return _calculateNewBitcoinCashTarget(blockChainSegmentId, blockId, blockHeight);
            }

            final Boolean requiresDifficultyEvaluation = (blockHeight % BLOCK_COUNT_PER_DIFFICULTY_ADJUSTMENT == 0);
            if (requiresDifficultyEvaluation) {
                return _calculateNewBitcoinCoreTarget(blockChainSegmentId, blockHeight, blockHeader);
            }

            if (Bip55.isEnabled(blockHeight)) {
                return _calculateBitcoinCashEmergencyDifficultyAdjustment(blockId, blockHeight, blockHeader);
            }

            final BlockId previousBlockBlockId = _blockDatabaseManager.getBlockIdFromHash(blockHeader.getPreviousBlockHash());
            if (previousBlockBlockId == null) { return null; }

            final BlockHeader previousBlockHeader = _blockDatabaseManager.getBlockHeader(previousBlockBlockId);
            return previousBlockHeader.getDifficulty();
        }
        catch (final DatabaseException exception) { Logger.log(exception); }

        return null;
    }
}
