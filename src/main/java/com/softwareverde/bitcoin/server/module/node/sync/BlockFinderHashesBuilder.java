package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;

public class BlockFinderHashesBuilder {
    protected final DatabaseManager _databaseManager;

    protected List<Sha256Hash> _createBlockFinderBlockHashes(final Boolean processedBlocksOnly, final Integer offset) throws DatabaseException {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();
        final BlockDatabaseManager blockDatabaseManager = _databaseManager.getBlockDatabaseManager();

        final Long maxBlockHeight;
        final BlockchainSegmentId headBlockchainSegmentId;
        final BlockId headBlockId;
        {
            final BlockId blockId;
            if (processedBlocksOnly) {
                blockId = blockDatabaseManager.getHeadBlockId();
            }
            else {
                blockId = blockHeaderDatabaseManager.getHeadBlockHeaderId();
            }

            headBlockId = blockHeaderDatabaseManager.getAncestorBlockId(blockId, offset);
        }
        if (headBlockId != null) {
            headBlockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(headBlockId);
            maxBlockHeight = blockHeaderDatabaseManager.getBlockHeight(headBlockId);
        }
        else {
            maxBlockHeight = 0L;
            headBlockchainSegmentId = null;
        }

        final MutableList<Sha256Hash> blockHashes = new MutableList<Sha256Hash>(BitcoinUtil.log2(maxBlockHeight.intValue()) + 11);
        int blockHeightStep = 1;
        for (long blockHeight = maxBlockHeight; blockHeight > 0L; blockHeight -= blockHeightStep) {
            final BlockId blockId = blockHeaderDatabaseManager.getBlockIdAtHeight(headBlockchainSegmentId, blockHeight);
            final Sha256Hash blockHash = blockHeaderDatabaseManager.getBlockHash(blockId);

            blockHashes.add(blockHash);

            if (blockHashes.getCount() >= 10) {
                blockHeightStep *= 2;
            }
        }

        return blockHashes;
    }

    public BlockFinderHashesBuilder(final DatabaseManager databaseManager) {
        _databaseManager = databaseManager;
    }

    public List<Sha256Hash> createBlockFinderBlockHashes() throws DatabaseException {
        return _createBlockFinderBlockHashes(true, 0);
    }

    public List<Sha256Hash> createBlockFinderBlockHashes(final Integer offset) throws DatabaseException {
        return _createBlockFinderBlockHashes(true, offset);
    }

    public List<Sha256Hash> createBlockHeaderFinderBlockHashes() throws DatabaseException {
        return _createBlockFinderBlockHashes(false, 0);
    }

    public List<Sha256Hash> createBlockHeaderFinderBlockHashes(final Integer offset) throws DatabaseException {
        return _createBlockFinderBlockHashes(false, offset);
    }
}
