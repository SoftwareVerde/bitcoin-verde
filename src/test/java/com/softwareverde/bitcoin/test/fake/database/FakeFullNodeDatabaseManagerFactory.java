package com.softwareverde.bitcoin.test.fake.database;

import com.softwareverde.bitcoin.server.module.node.database.block.fullnode.FullNodeBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.database.DatabaseException;

public class FakeFullNodeDatabaseManagerFactory extends FullNodeDatabaseManagerFactory {

    public FakeFullNodeDatabaseManagerFactory() {
        super(null, null, null, null, null);
    }

    protected BlockchainDatabaseManager _blockchainDatabaseManager = new FakeBlockchainDatabaseManager() { };
    protected BlockHeaderDatabaseManager _blockHeaderDatabaseManager = new MockBlockHeaderDatabaseManager();
    protected FullNodeBlockDatabaseManager _fullNodeBlockDatabaseManager = new MockBlockDatabaseManager();

    @Override
    public FullNodeDatabaseManager newDatabaseManager() throws DatabaseException {
        return new FullNodeDatabaseManager(null, null, null, null, null) {
            @Override
            public BlockchainDatabaseManager getBlockchainDatabaseManager() {
                return FakeFullNodeDatabaseManagerFactory.this._blockchainDatabaseManager;
            }

            @Override
            public BlockHeaderDatabaseManager getBlockHeaderDatabaseManager() {
                return FakeFullNodeDatabaseManagerFactory.this._blockHeaderDatabaseManager;
            }

            @Override
            public FullNodeBlockDatabaseManager getBlockDatabaseManager() {
                return FakeFullNodeDatabaseManagerFactory.this._fullNodeBlockDatabaseManager;
            }

            @Override
            public void close() throws DatabaseException { }
        };
    }

    public void setBlockchainDatabaseManager(final BlockchainDatabaseManager blockchainDatabaseManager) {
        _blockchainDatabaseManager = blockchainDatabaseManager;
    }

    public void setBlockHeaderDatabaseManager(final BlockHeaderDatabaseManager blockHeaderDatabaseManager) {
        _blockHeaderDatabaseManager = blockHeaderDatabaseManager;
    }

    public void setBlockDatabaseManager(final FullNodeBlockDatabaseManager fullNodeBlockDatabaseManager) {
        _fullNodeBlockDatabaseManager = fullNodeBlockDatabaseManager;
    }
}