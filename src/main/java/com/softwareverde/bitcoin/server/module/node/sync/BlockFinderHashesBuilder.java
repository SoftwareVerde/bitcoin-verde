package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.module.node.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;

public class BlockFinderHashesBuilder {
    protected final MysqlDatabaseConnection _databaseConnection;
    protected final DatabaseManagerCache _databaseManagerCache;

    public BlockFinderHashesBuilder(final MysqlDatabaseConnection databaseConnection, final DatabaseManagerCache databaseManagerCache) {
        _databaseConnection = databaseConnection;
        _databaseManagerCache = databaseManagerCache;
    }

    public List<Sha256Hash> createBlockFinderBlockHashes() throws DatabaseException {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(_databaseConnection, _databaseManagerCache);
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(_databaseConnection, _databaseManagerCache);

        final Long maxBlockHeight;
        final BlockchainSegmentId headBlockchainSegmentId;
        final BlockId headBlockId = blockDatabaseManager.getHeadBlockId();
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
        for (Long blockHeight = maxBlockHeight; blockHeight > 0L; blockHeight -= blockHeightStep) {
            final BlockId blockId = blockHeaderDatabaseManager.getBlockIdAtHeight(headBlockchainSegmentId, blockHeight);
            final Sha256Hash blockHash = blockHeaderDatabaseManager.getBlockHash(blockId);

            blockHashes.add(blockHash);

            if (blockHashes.getSize() >= 10) {
                blockHeightStep *= 2;
            }
        }

        return blockHashes;
    }
}
