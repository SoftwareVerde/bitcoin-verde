package com.softwareverde.bitcoin.server.module.node.database.core;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.cache.DisabledDatabaseManagerCache;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.address.core.CoreAddressDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.core.CoreBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.core.CoreBlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.pending.core.CorePendingBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.core.CoreBlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.node.core.CoreBitcoinNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.core.CoreTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.input.TransactionInputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.output.TransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.pending.PendingTransactionDatabaseManager;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.util.Util;

public class CoreDatabaseManager implements DatabaseManager {
    protected final DatabaseConnection _databaseConnection;
    protected final DatabaseManagerCache _databaseManagerCache;

    protected CoreBitcoinNodeDatabaseManager _nodeDatabaseManager;
    protected CoreBlockchainDatabaseManager _blockchainDatabaseManager;
    protected CoreBlockDatabaseManager _blockDatabaseManager;
    protected CoreBlockHeaderDatabaseManager _blockHeaderDatabaseManager;
    protected CorePendingBlockDatabaseManager _pendingBlockDatabaseManager;
    protected CoreAddressDatabaseManager _addressDatabaseManager;
    protected CoreTransactionDatabaseManager _transactionDatabaseManager;
    protected TransactionInputDatabaseManager _transactionInputDatabaseManager;
    protected TransactionOutputDatabaseManager _transactionOutputDatabaseManager;
    protected PendingTransactionDatabaseManager _pendingTransactionDatabaseManager;

    public CoreDatabaseManager(final DatabaseConnection databaseConnection) {
        _databaseConnection = databaseConnection;
        _databaseManagerCache = new DisabledDatabaseManagerCache();
    }

    public CoreDatabaseManager(final DatabaseConnection databaseConnection, final DatabaseManagerCache databaseManagerCache) {
        _databaseConnection = databaseConnection;
        _databaseManagerCache = Util.coalesce(databaseManagerCache, new DisabledDatabaseManagerCache());
    }

    @Override
    public DatabaseConnection getDatabaseConnection() {
        return _databaseConnection;
    }

    @Override
    public DatabaseManagerCache getDatabaseManagerCache() {
        return _databaseManagerCache;
    }

    @Override
    public CoreBitcoinNodeDatabaseManager getNodeDatabaseManager() {
        if (_nodeDatabaseManager == null) {
            _nodeDatabaseManager = new CoreBitcoinNodeDatabaseManager(this);
        }

        return _nodeDatabaseManager;
    }

    @Override
    public CoreBlockchainDatabaseManager getBlockchainDatabaseManager() {
        if (_blockchainDatabaseManager == null) {
            _blockchainDatabaseManager = new CoreBlockchainDatabaseManager(this);
        }

        return _blockchainDatabaseManager;
    }

    @Override
    public CoreBlockDatabaseManager getBlockDatabaseManager() {
        if (_blockDatabaseManager == null) {
            _blockDatabaseManager = new CoreBlockDatabaseManager(this);
        }

        return _blockDatabaseManager;
    }

    @Override
    public CoreBlockHeaderDatabaseManager getBlockHeaderDatabaseManager() {
        if (_blockHeaderDatabaseManager == null) {
            _blockHeaderDatabaseManager = new CoreBlockHeaderDatabaseManager(this);
        }

        return _blockHeaderDatabaseManager;
    }

    @Override
    public CorePendingBlockDatabaseManager getPendingBlockDatabaseManager() {
        if (_pendingBlockDatabaseManager == null) {
            _pendingBlockDatabaseManager = new CorePendingBlockDatabaseManager(this);
        }

        return _pendingBlockDatabaseManager;
    }

    @Override
    public CoreTransactionDatabaseManager getTransactionDatabaseManager() {
        if (_transactionDatabaseManager == null) {
            _transactionDatabaseManager = new CoreTransactionDatabaseManager(this);
        }

        return _transactionDatabaseManager;
    }

    public CoreAddressDatabaseManager getAddressDatabaseManager() {
        if (_addressDatabaseManager == null) {
            _addressDatabaseManager = new CoreAddressDatabaseManager(this);
        }

        return _addressDatabaseManager;
    }

    public TransactionInputDatabaseManager getTransactionInputDatabaseManager() {
        if (_transactionInputDatabaseManager == null) {
            _transactionInputDatabaseManager = new TransactionInputDatabaseManager(this);
        }

        return _transactionInputDatabaseManager;
    }

    public TransactionOutputDatabaseManager getTransactionOutputDatabaseManager() {
        if (_transactionOutputDatabaseManager == null) {
            _transactionOutputDatabaseManager = new TransactionOutputDatabaseManager(this);
        }

        return _transactionOutputDatabaseManager;
    }

    public PendingTransactionDatabaseManager getPendingTransactionDatabaseManager() {
        if (_pendingTransactionDatabaseManager == null) {
            _pendingTransactionDatabaseManager = new PendingTransactionDatabaseManager(this);
        }

        return _pendingTransactionDatabaseManager;
    }

    @Override
    public void close() throws DatabaseException {
        _databaseConnection.close();
    }
}
