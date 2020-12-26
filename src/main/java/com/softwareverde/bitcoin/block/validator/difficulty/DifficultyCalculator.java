package com.softwareverde.bitcoin.block.validator.difficulty;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.context.DifficultyCalculatorContext;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.DateUtil;
import com.softwareverde.util.Util;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

public class DifficultyCalculator {
    protected static final Integer BLOCK_COUNT_PER_DIFFICULTY_ADJUSTMENT = 2016;
    protected static final BigInteger TWO_TO_THE_POWER_OF_256 = BigInteger.valueOf(2L).pow(256);

    protected final DifficultyCalculatorContext _context;
    protected final MedianBlockHeaderSelector _medianBlockHeaderSelector;
    protected final AsertDifficultyCalculator _asertDifficultyCalculator;

    protected DifficultyCalculator(final DifficultyCalculatorContext blockchainContext, final MedianBlockHeaderSelector medianBlockHeaderSelector, final AsertDifficultyCalculator asertDifficultyCalculator) {
        _context = blockchainContext;
        _medianBlockHeaderSelector = medianBlockHeaderSelector;
        _asertDifficultyCalculator = asertDifficultyCalculator;
    }

    public DifficultyCalculator(final DifficultyCalculatorContext blockchainContext) {
        this(blockchainContext, new MedianBlockHeaderSelector(), new AsertDifficultyCalculator());
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
        final long parentBlockHeight = (forBlockHeight - 1L);
        final BlockHeader previousBlockHeader = _context.getBlockHeader(parentBlockHeight);

        final MedianBlockTime medianBlockTime = _context.getMedianBlockTime(parentBlockHeight);
        final MedianBlockTime medianBlockTimeForSixthBlock = _context.getMedianBlockTime(parentBlockHeight - 6L);
        final long secondsInTwelveHours = 43200L;

        if ( (medianBlockTime == null) || (medianBlockTimeForSixthBlock == null) ) {
            Logger.debug("Unable to calculate difficulty for Block height: " + forBlockHeight);
            return null;
        }

        final long timeBetweenMedianBlockTimes = (medianBlockTime.getCurrentTimeInSeconds() - medianBlockTimeForSixthBlock.getCurrentTimeInSeconds());
        if (timeBetweenMedianBlockTimes > secondsInTwelveHours) {
            final Difficulty newDifficulty = previousBlockHeader.getDifficulty().multiplyBy(1.25D);

            final Difficulty minimumDifficulty = Difficulty.BASE_DIFFICULTY;
            if (newDifficulty.isLessDifficultThan(minimumDifficulty)) {
                return minimumDifficulty;
            }

            Logger.debug("Emergency Difficulty Adjustment: BlockHeight: " + forBlockHeight + " Original Difficulty: " + previousBlockHeader.getDifficulty() + " New Difficulty: " + newDifficulty);
            return newDifficulty;
        }

        return _getParentDifficulty(forBlockHeight);
    }

    protected Difficulty _calculateAserti32dBitcoinCashTarget(final Long blockHeight) {
        final BlockHeader previousBlockHeader = _context.getBlockHeader((blockHeight > 0) ? (blockHeight - 1L) : 0L); // The ASERT algorithm uses the parent block's timestamp, except for the genesis block itself (which should never happen).
        final Long previousBlockTimestamp = previousBlockHeader.getTimestamp();

        final AsertReferenceBlock referenceBlock = _context.getAsertReferenceBlock();
        return _asertDifficultyCalculator.computeAsertTarget(referenceBlock, previousBlockTimestamp, blockHeight);
    }

    protected Difficulty _calculateCw144BitcoinCashTarget(final Long forBlockHeight) {
        final BlockHeader[] firstBlockHeaders = new BlockHeader[3]; // The oldest BlockHeaders...
        final BlockHeader[] lastBlockHeaders = new BlockHeader[3]; // The newest BlockHeaders...

        final Map<Sha256Hash, Long> blockHeights = new HashMap<Sha256Hash, Long>(6);

        // Set the lastBlockHeaders to be the head blockId, its parent, and its grandparent...
        final long parentBlockHeight = (forBlockHeight - 1L);
        for (int i = 0; i < lastBlockHeaders.length; ++i) {
            final Long blockHeight = (parentBlockHeight - i);

            final BlockHeader blockHeader = _context.getBlockHeader(blockHeight);
            if (blockHeader == null) { return null; }

            final Sha256Hash blockHash = blockHeader.getHash();
            blockHeights.put(blockHash, blockHeight);

            lastBlockHeaders[i] = blockHeader;
        }

        // Set the firstBlockHeaders to be the 144th, 145th, and 146th parent of the first block's parent...
        for (int i = 0; i < firstBlockHeaders.length; ++i) {
            final Long blockHeight = (parentBlockHeight - 144L - i);

            final BlockHeader blockHeader = _context.getBlockHeader(blockHeight);
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
            targetWork = TWO_TO_THE_POWER_OF_256
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

    protected Difficulty _getParentDifficulty(final Long blockHeight) {
        final Long previousBlockHeight = (blockHeight - 1L);
        final BlockHeader previousBlockHeader = _context.getBlockHeader(previousBlockHeight);
        return previousBlockHeader.getDifficulty();
    }

    public Difficulty calculateRequiredDifficulty(final Long blockHeight) {
        final UpgradeSchedule upgradeSchedule = _context.getUpgradeSchedule();

        final Boolean isFirstBlock = (Util.areEqual(0L, blockHeight));
        if (isFirstBlock) { return Difficulty.BASE_DIFFICULTY; }

        // final MedianBlockTime medianBlockTime = _context.getMedianBlockTime(blockHeight);
        final MedianBlockTime medianTimePast = _context.getMedianBlockTime(blockHeight - 1L);

        if (upgradeSchedule.isAsertDifficultyAdjustmentAlgorithmEnabled(medianTimePast)) {
            return _calculateAserti32dBitcoinCashTarget(blockHeight);
        }

        if (upgradeSchedule.isCw144DifficultyAdjustmentAlgorithmEnabled(blockHeight)) {
            return _calculateCw144BitcoinCashTarget(blockHeight);
        }

        final boolean requiresDifficultyEvaluation = (blockHeight % BLOCK_COUNT_PER_DIFFICULTY_ADJUSTMENT == 0);
        if (requiresDifficultyEvaluation) {
            return _calculateNewBitcoinCoreTarget(blockHeight);
        }

        if (upgradeSchedule.isEmergencyDifficultyAdjustmentAlgorithmEnabled(blockHeight)) {
            return _calculateBitcoinCashEmergencyDifficultyAdjustment(blockHeight);
        }

        return _getParentDifficulty(blockHeight);
    }
}
