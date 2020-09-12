package com.softwareverde.bitcoin.context.core;

import com.softwareverde.bitcoin.bip.HF20201115;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.validator.difficulty.AsertReferenceBlock;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.context.ContextException;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;

public class AsertReferenceBlockLoader {
    public interface ReferenceBlockLoaderContext {
        BlockId getHeadBlockIdOfBlockchainSegment(BlockchainSegmentId blockchainSegmentId) throws ContextException;
        MedianBlockTime getMedianBlockTime(BlockId blockId) throws ContextException;
        Long getBlockHeight(BlockId blockId) throws ContextException;
        BlockId getBlockIdAtHeight(BlockchainSegmentId blockchainSegmentId, Long blockHeight) throws ContextException;
        Difficulty getDifficulty(BlockId blockId) throws ContextException;
    }

    protected final ReferenceBlockLoaderContext _context;

    public AsertReferenceBlockLoader(final ReferenceBlockLoaderContext context) {
        _context = context;
    }

    // TODO: Hardcode value after 2020-11-15...
    public AsertReferenceBlock getAsertReferenceBlock(final BlockchainSegmentId blockchainSegmentId) throws ContextException {
        final BlockId headBlockId = _context.getHeadBlockIdOfBlockchainSegment(blockchainSegmentId);

        final MedianBlockTime headMedianBlockTime = _context.getMedianBlockTime(headBlockId);
        if (! HF20201115.isEnabled(headMedianBlockTime)) {
            Logger.debug("Cannot load Aserti3-2d anchor Block; HF has not activated.");
            return null;
        }

        final Long headBlockHeight = _context.getBlockHeight(headBlockId);

        Long maxBlockHeight = headBlockHeight;
        Long minBlockHeight = null;
        int parentCount = 1;
        while (true) {
            final BlockId blockId = _context.getBlockIdAtHeight(blockchainSegmentId, (headBlockHeight - parentCount)); // blockHeaderDatabaseManager.getAncestorBlockId(parentBlockId, parentCount);
            if (blockId == null) { break; }

            final MedianBlockTime medianBlockTime = _context.getMedianBlockTime(blockId);
            if (! HF20201115.isEnabled(medianBlockTime)) {
                minBlockHeight = _context.getBlockHeight(blockId);
                break;
            }
            else {
                maxBlockHeight = _context.getBlockHeight(blockId);
            }

            parentCount *= 2;
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
            if (! HF20201115.isEnabled(medianBlockTime)) {
                minBlockHeight = blockHeight;
            }
            else {
                maxBlockHeight = blockHeight;
            }
        }

        final Long blockHeight = maxBlockHeight;
        final BlockId blockId = _context.getBlockIdAtHeight(blockchainSegmentId, blockHeight);
        final Difficulty difficulty = _context.getDifficulty(blockId);
        final MedianBlockTime medianBlockTime = _context.getMedianBlockTime(blockId);

        return new AsertReferenceBlock(blockHeight, medianBlockTime, difficulty);
    }
}
