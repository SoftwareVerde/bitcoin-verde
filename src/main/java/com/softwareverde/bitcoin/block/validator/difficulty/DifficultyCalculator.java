package com.softwareverde.bitcoin.block.validator.difficulty;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegment;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.server.database.BlockChainDatabaseManager;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.io.Logger;
import com.softwareverde.util.DateUtil;

public class DifficultyCalculator {
    protected final BlockDatabaseManager _blockDatabaseManager;
    protected final BlockChainDatabaseManager _blockChainDatabaseManager;

    public DifficultyCalculator(final MysqlDatabaseConnection databaseConnection) {
        _blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
        _blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection);
    }

    public Difficulty calculateRequiredDifficulty(final BlockChainSegmentId blockChainSegmentId, final Block block) {
        final Integer blockCountPerDifficultyAdjustment = 2016;
        try {
            final BlockChainSegment blockChainSegment = _blockChainDatabaseManager.getBlockChainSegment(blockChainSegmentId);

            final BlockId blockId = _blockDatabaseManager.getBlockIdFromHash(block.getHash());
            if (blockId == null) {
                Logger.log("Unable to find BlockId from Hash: "+ block.getHash());
                return null;
            }

            final Long blockHeight = _blockDatabaseManager.getBlockHeightForBlockId(blockId); // blockChainSegment.getBlockHeight();  // NOTE: blockChainSegment.getBlockHeight() is not safe when replaying block-validation.
            if (blockHeight == null) {
                Logger.log("Invalid BlockHeight for BlockId: "+ blockId);
                return null;
            }

            final Boolean isFirstBlock = (blockChainSegment.getBlockHeight() == 0);
            final Boolean requiresDifficultyEvaluation = (blockHeight % blockCountPerDifficultyAdjustment == 0);
            if ( (requiresDifficultyEvaluation) && (! isFirstBlock) ) {
                //  Calculate the new difficulty. https://bitcoin.stackexchange.com/questions/5838/how-is-difficulty-calculated

                //  1. Get the block that is 2016 blocks behind the head block of this chain.
                final long previousBlockHeight = (blockHeight - blockCountPerDifficultyAdjustment); // NOTE: This is 2015 blocks worth of time (not 2016) because of a bug in Satoshi's implementation and is now part of the protocol definition.
                final BlockHeader blockWithPreviousAdjustment = _blockDatabaseManager.findBlockAtBlockHeight(blockChainSegmentId, previousBlockHeight);
                if (blockWithPreviousAdjustment == null) { return null; }

                //  2. Get the current block timestamp.
                final Long blockTimestamp;
                {
                    final BlockId previousBlockId = _blockDatabaseManager.getBlockIdFromHash(block.getPreviousBlockHash());
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
                final BlockHeader headBlockHeader = _blockDatabaseManager.getBlockHeader(blockChainSegment.getHeadBlockId());
                return headBlockHeader.getDifficulty();
            }
        }
        catch (final DatabaseException exception) { exception.printStackTrace(); }

        return null;
    }
}
