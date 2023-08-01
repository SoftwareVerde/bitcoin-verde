package com.softwareverde.bitcoin.context.lazy;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.context.ContextException;
import com.softwareverde.bitcoin.context.core.AsertReferenceBlockLoader;
import com.softwareverde.bitcoin.server.module.node.Blockchain;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.database.DatabaseException;

public class LazyReferenceBlockLoaderContext implements AsertReferenceBlockLoader.ReferenceBlockLoaderContext {
    protected final Blockchain _blockchain;
    protected final UpgradeSchedule _upgradeSchedule;

    public LazyReferenceBlockLoaderContext(final Blockchain blockchain, final UpgradeSchedule upgradeSchedule) {
        _blockchain = blockchain;
        _upgradeSchedule = upgradeSchedule;
    }

    @Override
    public BlockId getHeadBlockIdOfBlockchainSegment(final BlockchainSegmentId blockchainSegmentId) throws ContextException {
//        try {
//            return _blockchainDatabaseManager.getHeadBlockIdOfBlockchainSegment(blockchainSegmentId);
//        }
//        catch (final DatabaseException exception) {
//            throw new ContextException(exception);
//        }
        return null;
    }

    @Override
    public MedianBlockTime getMedianBlockTime(final BlockId blockId) throws ContextException {
//        try {
//            return _blockHeaderDatabaseManager.getMedianBlockTime(blockId);
//        }
//        catch (final DatabaseException exception) {
//            throw new ContextException(exception);
//        }
        return null;
    }

    @Override
    public Long getBlockTimestamp(final BlockId blockId) throws ContextException {
//        try {
//            return _blockHeaderDatabaseManager.getBlockTimestamp(blockId);
//        }
//        catch (final DatabaseException exception) {
//            throw new ContextException(exception);
//        }
        return null;
    }

    @Override
    public Long getBlockHeight(final BlockId blockId) throws ContextException {
//        try {
//            return _blockHeaderDatabaseManager.getBlockHeight(blockId);
//        }
//        catch (final DatabaseException exception) {
//            throw new ContextException(exception);
//        }
        return null;
    }

    @Override
    public BlockId getBlockIdAtHeight(final BlockchainSegmentId blockchainSegmentId, final Long blockHeight) throws ContextException {
//        try {
//            return _blockHeaderDatabaseManager.getBlockIdAtHeight(blockchainSegmentId, blockHeight);
//        }
//        catch (final DatabaseException exception) {
//            throw new ContextException(exception);
//        }
        return null;
    }

    @Override
    public Difficulty getDifficulty(final BlockId blockId) throws ContextException {
//        try {
//            final BlockHeader blockHeader = _blockHeaderDatabaseManager.getBlockHeader(blockId);
//            if (blockHeader == null) { return null; }
//
//            return blockHeader.getDifficulty();
//        }
//        catch (final DatabaseException exception) {
//            throw new ContextException(exception);
//        }
        return null;
    }

    @Override
    public UpgradeSchedule getUpgradeSchedule() {
        return _upgradeSchedule;
    }
}
