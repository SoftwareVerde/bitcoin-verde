package com.softwareverde.bitcoin.server.module.node.sync.bootstrap;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;

public class FullNodeHeadersBootstrapper extends HeadersBootstrapper {
    @Override
    protected List<BlockId> _insertBlockHeaders(final DatabaseManager databaseManager, final List<BlockHeader> batchedHeaders) throws DatabaseException {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();

        databaseManager.startTransaction();

        final List<BlockId> blockIds = blockHeaderDatabaseManager.insertBlockHeaders(batchedHeaders);

        databaseManager.commitTransaction();

        return blockIds;
    }

    public FullNodeHeadersBootstrapper(final FullNodeDatabaseManagerFactory databaseManagerFactory) {
        super(databaseManagerFactory);
    }
}
