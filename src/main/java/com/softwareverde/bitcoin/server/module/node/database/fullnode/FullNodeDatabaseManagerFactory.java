package com.softwareverde.bitcoin.server.module.node.database.fullnode;

import com.softwareverde.bitcoin.inflater.MasterInflater;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.module.node.store.PendingBlockStore;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManagerFactory;
import com.softwareverde.database.DatabaseException;

public class FullNodeDatabaseManagerFactory implements DatabaseManagerFactory {
    protected final DatabaseConnectionFactory _databaseConnectionFactory;
    protected final PendingBlockStore _blockStore;
    protected final MasterInflater _masterInflater;

    public FullNodeDatabaseManagerFactory(final DatabaseConnectionFactory databaseConnectionFactory, final PendingBlockStore blockStore, final MasterInflater masterInflater) {
        _databaseConnectionFactory = databaseConnectionFactory;
        _blockStore = blockStore;
        _masterInflater = masterInflater;
    }

    @Override
    public FullNodeDatabaseManager newDatabaseManager() throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection();
        return new FullNodeDatabaseManager(databaseConnection, _blockStore, _masterInflater);
    }

    @Override
    public DatabaseConnectionFactory getDatabaseConnectionFactory() {
        return _databaseConnectionFactory;
    }

    @Override
    public FullNodeDatabaseManagerFactory newDatabaseManagerFactory(final DatabaseConnectionFactory databaseConnectionFactory) {
        return new FullNodeDatabaseManagerFactory(databaseConnectionFactory, _blockStore, _masterInflater);
    }
}
