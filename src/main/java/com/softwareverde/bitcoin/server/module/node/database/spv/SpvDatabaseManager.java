package com.softwareverde.bitcoin.server.module.node.database.spv;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.cache.DisabledDatabaseManagerCache;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.core.CoreBlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.pending.spv.SpvPendingBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.spv.SpvBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.core.CoreBlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.node.spv.SpvBitcoinNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.spv.SpvTransactionDatabaseManager;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.util.Util;

public class SpvDatabaseManager implements DatabaseManager {
    protected final DatabaseConnection _databaseConnection;
    protected final DatabaseManagerCache _databaseManagerCache;

    protected SpvBitcoinNodeDatabaseManager _nodeDatabaseManager;
    protected CoreBlockchainDatabaseManager _blockchainDatabaseManager;
    protected SpvBlockDatabaseManager _blockDatabaseManager;
    protected CoreBlockHeaderDatabaseManager _blockHeaderDatabaseManager;
    protected SpvPendingBlockDatabaseManager _pendingBlockDatabaseManager;
    protected SpvTransactionDatabaseManager _transactionDatabaseManager;

    public SpvDatabaseManager(final DatabaseConnection databaseConnection) {
        _databaseConnection = databaseConnection;
        _databaseManagerCache = new DisabledDatabaseManagerCache();
    }

    public SpvDatabaseManager(final DatabaseConnection databaseConnection, final DatabaseManagerCache databaseManagerCache) {
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
    public SpvBitcoinNodeDatabaseManager getNodeDatabaseManager() {
        if (_nodeDatabaseManager == null) {
            _nodeDatabaseManager = new SpvBitcoinNodeDatabaseManager(this);
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
    public SpvBlockDatabaseManager getBlockDatabaseManager() {
        if (_blockDatabaseManager == null) {
            _blockDatabaseManager = new SpvBlockDatabaseManager(this);
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
    public SpvPendingBlockDatabaseManager getPendingBlockDatabaseManager() {
        if (_pendingBlockDatabaseManager == null) {
            _pendingBlockDatabaseManager = new SpvPendingBlockDatabaseManager();
        }

        return _pendingBlockDatabaseManager;
    }

    @Override
    public SpvTransactionDatabaseManager getTransactionDatabaseManager() {
        if (_transactionDatabaseManager == null) {
            _transactionDatabaseManager = new SpvTransactionDatabaseManager(this);
        }

        return _transactionDatabaseManager;
    }

    @Override
    public void close() throws DatabaseException {
        _databaseConnection.close();
    }
}
