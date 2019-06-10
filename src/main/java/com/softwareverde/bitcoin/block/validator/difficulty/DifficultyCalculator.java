package com.softwareverde.bitcoin.block.validator.difficulty;

import com.softwareverde.bitcoin.bip.Buip55;
import com.softwareverde.bitcoin.bip.HF20171113;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.database.DatabaseException;
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

    protected final DatabaseManager _databaseManager;

    public DifficultyCalculator(final DatabaseManager databaseManager) {
        _databaseManager = databaseManager;
    }

    protected Difficulty _calculateNewBitcoinCoreTarget(final BlockchainSegmentId blockchainSegmentId, final Long forBlockHeight, final BlockHeader nullableBlockHeader) throws DatabaseException {
        //  Calculate the new difficulty. https://bitcoin.stackexchange.com/questions/5838/how-is-difficulty-calculated

        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();

        final BlockId parentBlockId;
        if (nullableBlockHeader != null) {
            parentBlockId = blockHeaderDatabaseManager.getBlockHeaderId(nullableBlockHeader.getPreviousBlockHash());
        }
        else {
            parentBlockId = blockHeaderDatabaseManager.getBlockIdAtHeight(blockchainSegmentId, (forBlockHeight - 1L));
        }
        final BlockHeader parentBlockHeader = blockHeaderDatabaseManager.getBlockHeader(parentBlockId);

        //  1. Get the block that is 2016 blocks behind the head block of this chain.
        final long previousBlockHeight = (forBlockHeight - BLOCK_COUNT_PER_DIFFICULTY_ADJUSTMENT); // NOTE: This is 2015 blocks worth of time (not 2016) because of a bug in Satoshi's implementation and is now part of the protocol definition.
        final BlockId lastAdjustedBlockId = blockHeaderDatabaseManager.getBlockIdAtHeight(blockchainSegmentId, previousBlockHeight);
        final BlockHeader lastAdjustedBlockHeader = blockHeaderDatabaseManager.getBlockHeader(lastAdjustedBlockId);
        if (lastAdjustedBlockHeader == null) { return null; }

        //  2. Get the current block timestamp.
        final Long blockTimestamp = parentBlockHeader.getTimestamp();
        final Long previousBlockTimestamp = lastAdjustedBlockHeader.getTimestamp();

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
        final Difficulty newDifficulty = (parentBlockHeader.getDifficulty().multiplyBy(1.0D / boundedDifficultyAdjustment));

        //  8. The new difficulty cannot be less than the base difficulty.
        final Difficulty minimumDifficulty = Difficulty.BASE_DIFFICULTY;
        if (newDifficulty.isLessDifficultThan(minimumDifficulty)) {
            return minimumDifficulty;
        }

        return newDifficulty;
    }

    protected Difficulty _calculateBitcoinCashEmergencyDifficultyAdjustment(final BlockchainSegmentId blockchainSegmentId, final Long forBlockHeight, final BlockHeader nullableBlockHeader) throws DatabaseException {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();

        final BlockId previousBlockBlockId;
        if (nullableBlockHeader != null) {
            previousBlockBlockId = blockHeaderDatabaseManager.getBlockHeaderId(nullableBlockHeader.getPreviousBlockHash());
        }
        else {
            previousBlockBlockId = blockHeaderDatabaseManager.getBlockIdAtHeight(blockchainSegmentId, (forBlockHeight - 1));
        }
        if (previousBlockBlockId == null) { return null; }

        final BlockHeader previousBlockHeader = blockHeaderDatabaseManager.getBlockHeader(previousBlockBlockId);

        final MedianBlockTime medianBlockTime = blockHeaderDatabaseManager.calculateMedianBlockTimeStartingWithBlock(previousBlockBlockId);
        final BlockId sixthParentBlockId = blockHeaderDatabaseManager.getAncestorBlockId(previousBlockBlockId, 5);
        final MedianBlockTime medianBlockTimeForSixthBlock = blockHeaderDatabaseManager.calculateMedianBlockTime(sixthParentBlockId);
        final Long secondsInTwelveHours = 43200L;

        if (medianBlockTime == null || medianBlockTimeForSixthBlock == null) {
            Logger.log("Unable to calculate difficulty for block: " + (nullableBlockHeader != null ? nullableBlockHeader.getHash() : ("Height: " + forBlockHeight)));
            return null;
        }

        if (medianBlockTime.getCurrentTimeInSeconds() - medianBlockTimeForSixthBlock.getCurrentTimeInSeconds() > secondsInTwelveHours) {
            final Difficulty newDifficulty = previousBlockHeader.getDifficulty().multiplyBy(1.25D);

            final Difficulty minimumDifficulty = Difficulty.BASE_DIFFICULTY;
            if (newDifficulty.isLessDifficultThan(minimumDifficulty)) {
                return minimumDifficulty;
            }

            if (LOGGING_ENABLED) {
                Logger.log("Emergency Difficulty Adjustment: BlockHeight: " + forBlockHeight + " Original Difficulty: " + previousBlockHeader.getDifficulty() + " New Difficulty: " + newDifficulty);
            }
            return newDifficulty;
        }

        return previousBlockHeader.getDifficulty();
    }

    protected Difficulty _calculateNewBitcoinCashTarget(final BlockchainSegmentId blockchainSegmentId) throws DatabaseException {
        final BlockchainDatabaseManager blockchainDatabaseManager = _databaseManager.getBlockchainDatabaseManager();
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();

        final BlockHeader[] firstBlockHeaders = new BlockHeader[3]; // The oldest BlockHeaders...
        final BlockHeader[] lastBlockHeaders = new BlockHeader[3]; // The newest BlockHeaders...

        final BlockId currentHeadBlockId = blockchainDatabaseManager.getHeadBlockIdOfBlockchainSegment(blockchainSegmentId);
        final Long currentHeadBlockHeight = blockHeaderDatabaseManager.getBlockHeight(currentHeadBlockId);

        // Set the lastBlockHeaders to be the head blockId, its parent, and its grandparent...
        lastBlockHeaders[0] = blockHeaderDatabaseManager.getBlockHeader(currentHeadBlockId); // NOTE: The most-recent blockHeader is the current head Block...
        for (int i = 1; i < lastBlockHeaders.length; ++i) { // NOTICE: i = 1, not 0...
            final BlockId ancestorBlockId = blockHeaderDatabaseManager.getAncestorBlockId(currentHeadBlockId, i);
            final BlockHeader blockHeader = blockHeaderDatabaseManager.getBlockHeader(ancestorBlockId);
            if (blockHeader == null) { return null; }

            lastBlockHeaders[i] = blockHeader;
        }

        // Set the firstBlockHeaders to be the 144th, 145th, and 146th parent of blockId's parent...
        for (int i = 0; i < firstBlockHeaders.length; ++i) {
            final BlockId blockHeaderId = blockHeaderDatabaseManager.getBlockIdAtHeight(blockchainSegmentId, (currentHeadBlockHeight - 144L - i));
            if (blockHeaderId == null) { return null; }

            final BlockHeader blockHeader = blockHeaderDatabaseManager.getBlockHeader(blockHeaderId);
            firstBlockHeaders[i] = blockHeader;
        }

        return _calculateNewBitcoinCashTarget(firstBlockHeaders, lastBlockHeaders);
    }

    protected Difficulty _calculateNewBitcoinCashTarget(final BlockId blockId, final Long blockHeight) throws DatabaseException {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();

        final BlockHeader[] firstBlockHeaders = new BlockHeader[3]; // The oldest BlockHeaders...
        final BlockHeader[] lastBlockHeaders = new BlockHeader[3]; // The newest BlockHeaders...

        final BlockchainSegmentId blockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(blockId);

        // Set the lastBlockHeaders to be blockId's parent, its grandparent, and its great grandparent...
        for (int i = 0; i < lastBlockHeaders.length; ++i) {
            final BlockId ancestorBlockId = blockHeaderDatabaseManager.getAncestorBlockId(blockId, (i + 1));
            final BlockHeader blockHeader = blockHeaderDatabaseManager.getBlockHeader(ancestorBlockId);
            if (blockHeader == null) { return null; }

            lastBlockHeaders[i] = blockHeader;
        }

        // Set the firstBlockHeaders to be the 144th, 145th, and 146th parents of blockId's parent...
        for (int i = 0; i < firstBlockHeaders.length; ++i) {
            final Long parentBlockHeight = (blockHeight - 1);
            final BlockId blockHeaderId = blockHeaderDatabaseManager.getBlockIdAtHeight(blockchainSegmentId, (parentBlockHeight - 144L - i));
            if (blockHeaderId == null) { return null; }

            final BlockHeader blockHeader = blockHeaderDatabaseManager.getBlockHeader(blockHeaderId);
            firstBlockHeaders[i] = blockHeader;
        }

        return _calculateNewBitcoinCashTarget(firstBlockHeaders, lastBlockHeaders);
    }

    protected Difficulty _calculateNewBitcoinCashTarget(final BlockHeader[] firstBlockHeaders, final BlockHeader[] lastBlockHeaders) throws DatabaseException {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();

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

        final BlockId firstBlockId = blockHeaderDatabaseManager.getBlockHeaderId(firstBlockHeader.getHash());
        final BlockId lastBlockId = blockHeaderDatabaseManager.getBlockHeaderId(lastBlockHeader.getHash());
        final ChainWork firstChainWork = blockHeaderDatabaseManager.getChainWork(firstBlockId);
        final ChainWork lastChainWork = blockHeaderDatabaseManager.getChainWork(lastBlockId);

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

        final Difficulty newDifficulty = Difficulty.fromBigInteger(targetWork);

        final Difficulty minimumDifficulty = Difficulty.BASE_DIFFICULTY;
        if (newDifficulty.isLessDifficultThan(minimumDifficulty)) {
            return minimumDifficulty;
        }

        return newDifficulty;
    }

    public Difficulty calculateRequiredDifficulty(final BlockHeader blockHeader) {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();

        try {
            final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHeader.getHash());
            if (blockId == null) {
                Logger.log("Unable to find BlockId from Hash: "+ blockHeader.getHash());
                return null;
            }

            final Long blockHeight = blockHeaderDatabaseManager.getBlockHeight(blockId); // blockchainSegment.getBlockHeight();  // NOTE: blockchainSegment.getBlockHeight() is not safe when replaying block-validation.
            if (blockHeight == null) {
                Logger.log("Invalid BlockHeight for BlockId: "+ blockId);
                return null;
            }

            final Boolean isFirstBlock = (Util.areEqual(blockHeader.getHash(), BlockHeader.GENESIS_BLOCK_HASH)); // (blockchainSegment.getBlockHeight() == 0);
            if (isFirstBlock) { return Difficulty.BASE_DIFFICULTY; }

            if (HF20171113.isEnabled(blockHeight)) {
                return _calculateNewBitcoinCashTarget(blockId, blockHeight);
            }

            final boolean requiresDifficultyEvaluation = (blockHeight % BLOCK_COUNT_PER_DIFFICULTY_ADJUSTMENT == 0);
            if (requiresDifficultyEvaluation) {
                final BlockchainSegmentId blockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(blockId);
                return _calculateNewBitcoinCoreTarget(blockchainSegmentId, blockHeight, blockHeader);
            }

            if (Buip55.isEnabled(blockHeight)) {
                final BlockchainSegmentId blockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(blockId);
                return _calculateBitcoinCashEmergencyDifficultyAdjustment(blockchainSegmentId, blockHeight, blockHeader);
            }

            final BlockId previousBlockBlockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHeader.getPreviousBlockHash());
            if (previousBlockBlockId == null) { return null; }

            final BlockHeader previousBlockHeader = blockHeaderDatabaseManager.getBlockHeader(previousBlockBlockId);
            return previousBlockHeader.getDifficulty();
        }
        catch (final DatabaseException exception) { Logger.log(exception); }

        return null;
    }

    public Difficulty calculateRequiredDifficulty() {
        final BlockchainDatabaseManager blockchainDatabaseManager = _databaseManager.getBlockchainDatabaseManager();
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();

        try {
            final BlockId parentBlockId = blockHeaderDatabaseManager.getHeadBlockHeaderId();
            if (parentBlockId == null) { return Difficulty.BASE_DIFFICULTY; } // Special case for the Genesis block...

            final Long blockHeight = (blockHeaderDatabaseManager.getBlockHeight(parentBlockId) + 1);

            final BlockchainSegmentId blockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();

            if (HF20171113.isEnabled(blockHeight)) {
                return _calculateNewBitcoinCashTarget(blockchainSegmentId);
            }

            final Boolean requiresDifficultyEvaluation = (blockHeight % BLOCK_COUNT_PER_DIFFICULTY_ADJUSTMENT == 0);
            if (requiresDifficultyEvaluation) {
                return _calculateNewBitcoinCoreTarget(blockchainSegmentId, blockHeight, null);
            }

            if (Buip55.isEnabled(blockHeight)) {
                return _calculateBitcoinCashEmergencyDifficultyAdjustment(blockchainSegmentId, blockHeight, null);
            }

            final BlockHeader parentBlockHeader = blockHeaderDatabaseManager.getBlockHeader(parentBlockId);
            return parentBlockHeader.getDifficulty();
        }
        catch (final DatabaseException exception) { Logger.log(exception); }

        return null;
    }
}
