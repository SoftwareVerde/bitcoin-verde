package com.softwareverde.bitcoin.block.validator.difficulty;

import com.softwareverde.bitcoin.bip.Bip55;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.header.difficulty.ImmutableDifficulty;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.chain.time.MedianBlockTimeWithBlocks;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.io.Logger;
import com.softwareverde.util.DateUtil;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.Util;

public class DifficultyCalculator {
    protected final BlockDatabaseManager _blockDatabaseManager;
    protected final MedianBlockTimeWithBlocks _medianBlockTime;

    public DifficultyCalculator(final MysqlDatabaseConnection databaseConnection, final MedianBlockTimeWithBlocks medianBlockTime) {
        _blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
        _medianBlockTime = medianBlockTime;
    }

    public Difficulty calculateRequiredDifficulty(final BlockChainSegmentId blockChainSegmentId, final BlockHeader blockHeader) {
        final Integer blockCountPerDifficultyAdjustment = 2016;
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

            final Boolean requiresDifficultyEvaluation = (blockHeight % blockCountPerDifficultyAdjustment == 0);
            if (requiresDifficultyEvaluation) {
                //  Calculate the new difficulty. https://bitcoin.stackexchange.com/questions/5838/how-is-difficulty-calculated

                //  1. Get the block that is 2016 blocks behind the head block of this chain.
                final long previousBlockHeight = (blockHeight - blockCountPerDifficultyAdjustment); // NOTE: This is 2015 blocks worth of time (not 2016) because of a bug in Satoshi's implementation and is now part of the protocol definition.
                final BlockHeader blockWithPreviousAdjustment = _blockDatabaseManager.findBlockAtBlockHeight(blockChainSegmentId, previousBlockHeight);
                if (blockWithPreviousAdjustment == null) { return null; }

                //  2. Get the current block timestamp.
                final Long blockTimestamp;
                {
                    final BlockId previousBlockId = _blockDatabaseManager.getBlockIdFromHash(blockHeader.getPreviousBlockHash());
                    final BlockHeader previousBlock = _blockDatabaseManager.getBlockHeader(previousBlockId);
                    blockTimestamp = previousBlock.getTimestamp();
                }
                final Long previousBlockTimestamp = blockWithPreviousAdjustment.getTimestamp();

                Logger.log(DateUtil.Utc.timestampToDatetimeString(blockTimestamp * 1000L));
                Logger.log(DateUtil.Utc.timestampToDatetimeString(previousBlockTimestamp * 1000L));

                //  3. Calculate the difference between the network-time and the time of the 2015th-parent block ("secondsElapsed"). (NOTE: 2015 instead of 2016 due to protocol bug.)
                final Long secondsElapsed = (blockTimestamp - previousBlockTimestamp);
                Logger.log("2016 blocks in "+ secondsElapsed + " ("+ (secondsElapsed/60F/60F/24F) +" days)");

                //  4. Calculate the desired two-weeks elapse-time ("secondsInTwoWeeks").
                final Long secondsInTwoWeeks = 2L * 7L * 24L * 60L * 60L; // <Week Count> * <Days / Week> * <Hours / Day> * <Minutes / Hour> * <Seconds / Minute>

                //  5. Calculate the difficulty adjustment via (secondsInTwoWeeks / secondsElapsed) ("difficultyAdjustment").
                final double difficultyAdjustment = (secondsInTwoWeeks.doubleValue() / secondsElapsed.doubleValue());
                Logger.log("Adjustment: "+ difficultyAdjustment);

                //  6. Bound difficultyAdjustment between [4, 0.25].
                final double boundedDifficultyAdjustment = (Math.min(4D, Math.max(0.25D, difficultyAdjustment)));

                //  7. Multiply the difficulty by the bounded difficultyAdjustment.
                final Difficulty newDifficulty = (blockWithPreviousAdjustment.getDifficulty().multiplyBy(1.0D / boundedDifficultyAdjustment));

                //  8. The new difficulty cannot be less than the base difficulty.
                final Difficulty minimumDifficulty = Difficulty.BASE_DIFFICULTY;
                if (newDifficulty.isLessDifficultThan(minimumDifficulty)) {
                    return minimumDifficulty;
                }

                return newDifficulty;
            }
            else {
                final BlockId previousBlockBlockId = _blockDatabaseManager.getBlockIdFromHash(blockHeader.getPreviousBlockHash());
                if (previousBlockBlockId == null) { return null; }

                final BlockHeader headBlockHeader = _blockDatabaseManager.getBlockHeader(previousBlockBlockId);

                if (Bip55.isEnabled(blockHeight)) {
                    // 00000000000000000012C195D050EDF9D2C0F7CAF733E959A8E38D60160194CA
                    // Required: 18014735 Found: 18019902
                    final BlockHeader tipBlockHeader = _medianBlockTime.getBlockHeader(0);
                    final BlockHeader blockHeaderSixBlocksAgo = _medianBlockTime.getBlockHeader(6);
                    final Long secondsInTwelveHours = 43200L;

                    if (tipBlockHeader.getTimestamp() - blockHeaderSixBlocksAgo.getTimestamp() > secondsInTwelveHours) {

                        final Difficulty emergencyDifficulty;
                        {
                            Difficulty newDifficulty = headBlockHeader.getDifficulty().multiplyBy(1.25D);

                            final Difficulty minimumDifficulty = Difficulty.BASE_DIFFICULTY;
                            if (newDifficulty.isLessDifficultThan(minimumDifficulty)) {
                                newDifficulty = minimumDifficulty;
                            }
                            emergencyDifficulty = newDifficulty;
                        }

                        Logger.log("Emergency Difficulty Adjustment: BlockHeight: " + blockHeight + " Original Difficulty: " + headBlockHeader.getDifficulty() + " New Difficulty: " + emergencyDifficulty);
                        return emergencyDifficulty;
                    }
                }

                return headBlockHeader.getDifficulty();
            }
        }
        catch (final DatabaseException exception) { exception.printStackTrace(); }

        return null;
    }
}
