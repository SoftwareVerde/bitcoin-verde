package com.softwareverde.bitcoin.server.module.node.database.fullnode;

import com.softwareverde.bitcoin.inflater.MasterInflater;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.module.node.BlockCache;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.NonIndexingFullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.input.NonIndexingTransactionInputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.output.NonIndexingTransactionOutputDatabaseManager;

public class NonIndexingFullNodeDatabaseManager extends FullNodeDatabaseManager {
    protected final BlockCache _blockCache;
    protected final MasterInflater _masterInflater;

    public NonIndexingFullNodeDatabaseManager(final DatabaseConnection databaseConnection, final BlockCache blockCache, final MasterInflater masterInflater) {
        super(databaseConnection);
        _blockCache = blockCache;
        _masterInflater = masterInflater;
    }

    public NonIndexingFullNodeDatabaseManager(final DatabaseConnection databaseConnection, final DatabaseManagerCache databaseManagerCache, final BlockCache blockCache, final MasterInflater masterInflater) {
        super(databaseConnection, databaseManagerCache);
        _blockCache = blockCache;
        _masterInflater = masterInflater;
    }

    @Override
    public NonIndexingFullNodeTransactionDatabaseManager getTransactionDatabaseManager() {
        if (_transactionDatabaseManager == null) {
            _transactionDatabaseManager = new NonIndexingFullNodeTransactionDatabaseManager(this, _blockCache, _masterInflater);
        }

        return (NonIndexingFullNodeTransactionDatabaseManager) _transactionDatabaseManager;
    }

    public NonIndexingTransactionInputDatabaseManager getTransactionInputDatabaseManager() {
        if (_transactionInputDatabaseManager == null) {
            _transactionInputDatabaseManager = new NonIndexingTransactionInputDatabaseManager(this);
        }

        return (NonIndexingTransactionInputDatabaseManager) _transactionInputDatabaseManager;
    }

    public NonIndexingTransactionOutputDatabaseManager getTransactionOutputDatabaseManager() {
        if (_transactionOutputDatabaseManager == null) {
            _transactionOutputDatabaseManager = new NonIndexingTransactionOutputDatabaseManager(this);
        }

        return (NonIndexingTransactionOutputDatabaseManager) _transactionOutputDatabaseManager;
    }
}
