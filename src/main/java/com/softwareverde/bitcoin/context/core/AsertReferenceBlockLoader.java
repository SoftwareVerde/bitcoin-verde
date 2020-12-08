package com.softwareverde.bitcoin.context.core;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.validator.difficulty.AsertReferenceBlock;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.context.ContextException;
import com.softwareverde.bitcoin.context.UpgradeScheduleContext;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;

public class AsertReferenceBlockLoader {
    public interface ReferenceBlockLoaderContext extends UpgradeScheduleContext {
        BlockId getHeadBlockIdOfBlockchainSegment(BlockchainSegmentId blockchainSegmentId) throws ContextException;
        MedianBlockTime getMedianBlockTime(BlockId blockId) throws ContextException;
        Long getBlockTimestamp(BlockId blockId) throws ContextException;
        Long getBlockHeight(BlockId blockId) throws ContextException;
        BlockId getBlockIdAtHeight(BlockchainSegmentId blockchainSegmentId, Long blockHeight) throws ContextException;
        Difficulty getDifficulty(BlockId blockId) throws ContextException;
    }

    protected final ReferenceBlockLoaderContext _context;

    public AsertReferenceBlockLoader(final ReferenceBlockLoaderContext context) {
        _context = context;
    }

    public AsertReferenceBlock getAsertReferenceBlock(final BlockchainSegmentId blockchainSegmentId) throws ContextException {
        final UpgradeSchedule upgradeSchedule = _context.getUpgradeSchedule();
        final BlockId headBlockId = _context.getHeadBlockIdOfBlockchainSegment(blockchainSegmentId);

        final MedianBlockTime headMedianBlockTime = _context.getMedianBlockTime(headBlockId);
        if (! upgradeSchedule.isAsertDifficultyAdjustmentAlgorithmEnabled(headMedianBlockTime)) {
            Logger.debug("Cannot load Aserti3-2d anchor Block; HF has not activated.");
            return null;
        }

        final Long headBlockHeight = _context.getBlockHeight(headBlockId);

        Long maxBlockHeight = headBlockHeight;
        Long minBlockHeight = null;
        int scaleFactor = 2016;
        int parentCount = 144;
        while (true) {
            final BlockId blockId = _context.getBlockIdAtHeight(blockchainSegmentId, (headBlockHeight - parentCount)); // blockHeaderDatabaseManager.getAncestorBlockId(parentBlockId, parentCount);
            if (blockId == null) { break; }

            final MedianBlockTime medianBlockTime = _context.getMedianBlockTime(blockId);
            if (! upgradeSchedule.isAsertDifficultyAdjustmentAlgorithmEnabled(medianBlockTime)) {
                minBlockHeight = _context.getBlockHeight(blockId);
                break;
            }
            else {
                maxBlockHeight = _context.getBlockHeight(blockId);
            }

            parentCount += scaleFactor;
            scaleFactor *= 2;
        }

        if (minBlockHeight == null) {
            Logger.debug("No anchor Block found.");
            return null;
        }

        while (true) {
            final Long blockHeight = ((maxBlockHeight + minBlockHeight) / 2L);
            if (Util.areEqual(minBlockHeight, blockHeight)) { break; }

            final BlockId blockId = _context.getBlockIdAtHeight(blockchainSegmentId, blockHeight);
            if (blockId == null) {
                Logger.debug("No anchor Block found.");
                return null;
            }

            final MedianBlockTime medianBlockTime = _context.getMedianBlockTime(blockId);
            if (! upgradeSchedule.isAsertDifficultyAdjustmentAlgorithmEnabled(medianBlockTime)) {
                minBlockHeight = blockHeight;
            }
            else {
                maxBlockHeight = blockHeight;
            }
        }

        final Long blockHeight = maxBlockHeight;
        final Long parentBlockHeight = ((blockHeight > 0L) ? (blockHeight - 1L) : 0L);
        final BlockId blockId = _context.getBlockIdAtHeight(blockchainSegmentId, blockHeight);
        final BlockId parentBlockId = _context.getBlockIdAtHeight(blockchainSegmentId, parentBlockHeight);

        final Difficulty difficulty = _context.getDifficulty(blockId);
        final Long previousBlockTimestamp = _context.getBlockTimestamp(parentBlockId);

        return new AsertReferenceBlock(blockHeight, previousBlockTimestamp, difficulty);
    }
}
