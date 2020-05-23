package com.softwareverde.bitcoin.block.validator.difficulty;

import com.softwareverde.bitcoin.bip.*;
import com.softwareverde.bitcoin.block.header.*;
import com.softwareverde.bitcoin.block.header.difficulty.*;
import com.softwareverde.bitcoin.block.header.difficulty.work.*;
import com.softwareverde.bitcoin.chain.time.*;
import com.softwareverde.bitcoin.context.*;
import com.softwareverde.logging.*;
import com.softwareverde.security.hash.sha256.*;
import com.softwareverde.util.*;

import java.math.*;
import java.util.*;

public class DifficultyCalculator<Context extends BlockHeaderContext & ChainWorkContext & MedianBlockTimeContext> {
    protected static final Integer BLOCK_COUNT_PER_DIFFICULTY_ADJUSTMENT = 2016;
    protected static final BigInteger TWO_RAISED_TO_256 = BigInteger.valueOf(2L).pow(256);

    protected final MedianBlockHeaderSelector _medianBlockHeaderSelector;
    protected final Context _context;

    protected DifficultyCalculator(final Context blockchainContext, final MedianBlockHeaderSelector medianBlockHeaderSelector) {
        _context = blockchainContext;
        _medianBlockHeaderSelector = medianBlockHeaderSelector;
    }

    public DifficultyCalculator(final Context blockchainContext) {
        this(blockchainContext, new MedianBlockHeaderSelector());
    }

    protected Difficulty _calculateNewBitcoinCoreTarget(final Long forBlockHeight) {
        //  Calculate the new difficulty. https://bitcoin.stackexchange.com/questions/5838/how-is-difficulty-calculated
        final BlockHeader parentBlockHeader = _context.getBlockHeader(forBlockHeight - 1L);

        //  1. Get the block that is 2016 blocks behind the head block of this chain.
        final long blockHeightOfPreviousAdjustment = (forBlockHeight - BLOCK_COUNT_PER_DIFFICULTY_ADJUSTMENT); // NOTE: This is 2015 blocks worth of time (not 2016) because of a bug in Satoshi's implementation and is now part of the protocol definition.
        final BlockHeader lastAdjustedBlockHeader = _context.getBlockHeader(blockHeightOfPreviousAdjustment);
        if (lastAdjustedBlockHeader == null) { return null; }

        //  2. Get the current block timestamp.
        final long blockTimestamp = parentBlockHeader.getTimestamp();
        final long previousBlockTimestamp = lastAdjustedBlockHeader.getTimestamp();

        Logger.trace(DateUtil.Utc.timestampToDatetimeString(blockTimestamp * 1000L));
        Logger.trace(DateUtil.Utc.timestampToDatetimeString(previousBlockTimestamp * 1000L));

        //  3. Calculate the difference between the network-time and the time of the 2015th-parent block ("secondsElapsed"). (NOTE: 2015 instead of 2016 due to protocol bug.)
        final long secondsElapsed = (blockTimestamp - previousBlockTimestamp);
        Logger.trace("2016 blocks in " + secondsElapsed + " (" + (secondsElapsed / 60F / 60F / 24F) + " days)");

        //  4. Calculate the desired two-weeks elapse-time ("secondsInTwoWeeks").
        final long secondsInTwoWeeks = (2L * 7L * 24L * 60L * 60L); // <Week Count> * <Days / Week> * <Hours / Day> * <Minutes / Hour> * <Seconds / Minute>

        //  5. Calculate the difficulty adjustment via (secondsInTwoWeeks / secondsElapsed) ("difficultyAdjustment").
        final double difficultyAdjustment = ( ((double) secondsInTwoWeeks) / ((double) secondsElapsed) );
        Logger.trace("Adjustment: " + difficultyAdjustment);

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

    protected Difficulty _calculateBitcoinCashEmergencyDifficultyAdjustment(final Long forBlockHeight) {
        final BlockHeader previousBlockHeader = _context.getBlockHeader(forBlockHeight - 1L);

        final MedianBlockTime medianBlockTime = _context.getMedianBlockTime(forBlockHeight - 1L); // blockHeaderDatabaseManager.calculateMedianBlockTimeStartingWithBlock(previousBlockBlockId);
        // final BlockId sixthParentBlockId = blockHeaderDatabaseManager.getAncestorBlockId(previousBlockBlockId, 5);
        final MedianBlockTime medianBlockTimeForSixthBlock = _context.getMedianBlockTime(forBlockHeight - 6L); // blockHeaderDatabaseManager.calculateMedianBlockTime(sixthParentBlockId);
        final long secondsInTwelveHours = 43200L;

        if ( (medianBlockTime == null) || (medianBlockTimeForSixthBlock == null) ) {
            Logger.warn("Unable to calculate difficulty for Block height: " + forBlockHeight);
            return null;
        }

        final long timeBetweenMedianBlockTimes = (medianBlockTime.getCurrentTimeInSeconds() - medianBlockTimeForSixthBlock.getCurrentTimeInSeconds());
        if (timeBetweenMedianBlockTimes > secondsInTwelveHours) {
            final Difficulty newDifficulty = previousBlockHeader.getDifficulty().multiplyBy(1.25D);

            final Difficulty minimumDifficulty = Difficulty.BASE_DIFFICULTY;
            if (newDifficulty.isLessDifficultThan(minimumDifficulty)) {
                return minimumDifficulty;
            }

            Logger.info("Emergency Difficulty Adjustment: BlockHeight: " + forBlockHeight + " Original Difficulty: " + previousBlockHeader.getDifficulty() + " New Difficulty: " + newDifficulty);
            return newDifficulty;
        }

        return previousBlockHeader.getDifficulty();
    }

    protected Difficulty _calculateNewBitcoinCashTarget(final Long forBlockHeight) {
        final BlockHeader[] firstBlockHeaders = new BlockHeader[3]; // The oldest BlockHeaders...
        final BlockHeader[] lastBlockHeaders = new BlockHeader[3]; // The newest BlockHeaders...

        final Map<Sha256Hash, Long> blockHeights = new HashMap<Sha256Hash, Long>(6);

        // Set the lastBlockHeaders to be the head blockId, its parent, and its grandparent...
        lastBlockHeaders[0] = _context.getBlockHeader(forBlockHeight); // NOTE: The most-recent blockHeader is the current head Block...
        for (int i = 1; i < lastBlockHeaders.length; ++i) { // NOTICE: i = 1, not 0...
            final Long blockHeight = (forBlockHeight - i);

            // final BlockId ancestorBlockId = blockHeaderDatabaseManager.getAncestorBlockId(currentHeadBlockId, i);
            final BlockHeader blockHeader = _context.getBlockHeader(blockHeight); // blockHeaderDatabaseManager.getBlockHeader(ancestorBlockId);
            if (blockHeader == null) { return null; }

            final Sha256Hash blockHash = blockHeader.getHash();
            blockHeights.put(blockHash, blockHeight);

            lastBlockHeaders[i] = blockHeader;
        }

        // Set the firstBlockHeaders to be the 144th, 145th, and 146th parent of blockId's parent...
        for (int i = 0; i < firstBlockHeaders.length; ++i) {
            final Long blockHeight = (forBlockHeight - 144L - i);

            // final BlockId blockHeaderId = blockHeaderDatabaseManager.getBlockIdAtHeight(blockchainSegmentId, (currentHeadBlockHeight - 144L - i));
            // if (blockHeaderId == null) { return null; }
            final BlockHeader blockHeader = _context.getBlockHeader(blockHeight); // blockHeaderDatabaseManager.getBlockHeader(blockHeaderId);
            if (blockHeader == null) { return null; }

            final Sha256Hash blockHash = blockHeader.getHash();
            blockHeights.put(blockHash, blockHeight);

            firstBlockHeaders[i] = blockHeader;
        }

        final BlockHeader firstBlockHeader = _medianBlockHeaderSelector.selectMedianBlockHeader(firstBlockHeaders);
        final Sha256Hash firstBlockHash = firstBlockHeader.getHash();
        final Long firstBlockHeight = blockHeights.get(firstBlockHash);

        final BlockHeader lastBlockHeader = _medianBlockHeaderSelector.selectMedianBlockHeader(lastBlockHeaders);
        final Sha256Hash lastBlockHash = lastBlockHeader.getHash();
        final Long lastBlockHeight = blockHeights.get(lastBlockHash);

        final long timeSpan;
        {
            final long minimumValue = (72L * 600L);
            final long maximumValue = (288L * 600L);
            final long difference = (lastBlockHeader.getTimestamp() - firstBlockHeader.getTimestamp());

            if (difference < minimumValue) {
                timeSpan = minimumValue;
            }
            else {
                timeSpan = Math.min(difference, maximumValue);
            }
        }

        // final BlockId firstBlockId = blockHeaderDatabaseManager.getBlockHeaderId(firstBlockHeader.getHash());
        // final BlockId lastBlockId = blockHeaderDatabaseManager.getBlockHeaderId(lastBlockHeader.getHash());
        final ChainWork firstChainWork = _context.getChainWork(firstBlockHeight); // blockHeaderDatabaseManager.getChainWork(firstBlockId);
        final ChainWork lastChainWork = _context.getChainWork(lastBlockHeight);

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

    public Difficulty calculateRequiredDifficulty(final Long blockHeight) {
        final Boolean isFirstBlock = (Util.areEqual(0L, blockHeight));
        if (isFirstBlock) { return Difficulty.BASE_DIFFICULTY; }

        if (HF20171113.isEnabled(blockHeight)) {
            return _calculateNewBitcoinCashTarget(blockHeight);
        }

        final boolean requiresDifficultyEvaluation = (blockHeight % BLOCK_COUNT_PER_DIFFICULTY_ADJUSTMENT == 0);
        if (requiresDifficultyEvaluation) {
            return _calculateNewBitcoinCoreTarget(blockHeight);
        }

        if (Buip55.isEnabled(blockHeight)) {
            return _calculateBitcoinCashEmergencyDifficultyAdjustment(blockHeight);
        }

        final Long previousBlockHeight = (blockHeight - 1L);
        final BlockHeader previousBlockHeader = _context.getBlockHeader(previousBlockHeight);
        return previousBlockHeader.getDifficulty();
    }
}
