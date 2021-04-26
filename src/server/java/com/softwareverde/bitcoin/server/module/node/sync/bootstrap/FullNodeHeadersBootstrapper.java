package com.softwareverde.bitcoin.server.module.node.sync.bootstrap;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.util.TransactionUtil;

public class FullNodeHeadersBootstrapper extends HeadersBootstrapper {
    @Override
    protected List<BlockId> _insertBlockHeaders(final DatabaseManager databaseManager, final List<BlockHeader> batchedHeaders) throws DatabaseException {
        final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();

        TransactionUtil.startTransaction(databaseConnection);

        final List<BlockId> blockIds = blockHeaderDatabaseManager.insertBlockHeaders(batchedHeaders);

        TransactionUtil.commitTransaction(databaseConnection);

        return blockIds;
    }

    public FullNodeHeadersBootstrapper(final FullNodeDatabaseManagerFactory databaseManagerFactory, final Boolean insertPendingBlocks) {
        super(databaseManagerFactory);
    }
}
