package com.softwareverde.bitcoin.server.module.node.sync.bootstrap;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.pending.fullnode.FullNodePendingBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.util.Util;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.util.TransactionUtil;

public class FullNodeHeadersBootstrapper extends HeadersBootstrapper {
    protected final Boolean _shouldInsertPendingBlocks;

    @Override
    protected List<BlockId> _insertBlockHeaders(final DatabaseManager databaseManager, final List<BlockHeader> batchedHeaders) throws DatabaseException {
        final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();

        TransactionUtil.startTransaction(databaseConnection);

        final List<BlockId> blockIds = blockHeaderDatabaseManager.insertBlockHeaders(batchedHeaders);

        if (_shouldInsertPendingBlocks) {
            final FullNodeDatabaseManager fullNodeDatabaseManager = (FullNodeDatabaseManager) databaseManager;
            final FullNodePendingBlockDatabaseManager pendingBlockDatabaseManager = fullNodeDatabaseManager.getPendingBlockDatabaseManager();
            for (final BlockHeader blockHeader : batchedHeaders) {
                final Sha256Hash blockHash = blockHeader.getHash();
                final Sha256Hash previousBlockHash = blockHeader.getPreviousBlockHash();
                final Boolean pendingBlockConnectedToMainChain = pendingBlockDatabaseManager.isPendingBlockConnectedToMainChain(blockHash);
                if (! Util.coalesce(pendingBlockConnectedToMainChain, true)) { break; }

                pendingBlockDatabaseManager.storeBlockHash(blockHash, previousBlockHash);
            }
        }

        TransactionUtil.commitTransaction(databaseConnection);

        return blockIds;
    }

    public FullNodeHeadersBootstrapper(final FullNodeDatabaseManagerFactory databaseManagerFactory, final Boolean insertPendingBlocks) {
        super(databaseManagerFactory);
        _shouldInsertPendingBlocks = insertPendingBlocks;
    }
}
