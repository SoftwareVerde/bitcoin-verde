package com.softwareverde.bitcoin.server.module.node.database.fullnode;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.cache.DisabledDatabaseManagerCache;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.address.fullnode.FullNodeAddressDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.fullnode.FullNodeBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.fullnode.FullNodeBlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.pending.fullnode.FullNodePendingBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManagerCore;
import com.softwareverde.bitcoin.server.module.node.database.node.fullnode.FullNodeBitcoinNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.node.fullnode.FullNodeBitcoinNodeDatabaseManagerCore;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManagerCore;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.input.TransactionInputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.output.TransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.pending.PendingTransactionDatabaseManager;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.util.Util;

public class FullNodeDatabaseManager implements DatabaseManager {
    protected final DatabaseConnection _databaseConnection;
    protected final DatabaseManagerCache _databaseManagerCache;

    protected FullNodeBitcoinNodeDatabaseManager _nodeDatabaseManager;
    protected BlockchainDatabaseManagerCore _blockchainDatabaseManager;
    protected FullNodeBlockDatabaseManager _blockDatabaseManager;
    protected FullNodeBlockHeaderDatabaseManager _blockHeaderDatabaseManager;
    protected FullNodePendingBlockDatabaseManager _pendingBlockDatabaseManager;
    protected FullNodeAddressDatabaseManager _addressDatabaseManager;
    protected FullNodeTransactionDatabaseManager _transactionDatabaseManager;
    protected TransactionInputDatabaseManager _transactionInputDatabaseManager;
    protected TransactionOutputDatabaseManager _transactionOutputDatabaseManager;
    protected PendingTransactionDatabaseManager _pendingTransactionDatabaseManager;

    public FullNodeDatabaseManager(final DatabaseConnection databaseConnection) {
        _databaseConnection = databaseConnection;
        _databaseManagerCache = new DisabledDatabaseManagerCache();
    }

    public FullNodeDatabaseManager(final DatabaseConnection databaseConnection, final DatabaseManagerCache databaseManagerCache) {
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
    public FullNodeBitcoinNodeDatabaseManager getNodeDatabaseManager() {
        if (_nodeDatabaseManager == null) {
            _nodeDatabaseManager = new FullNodeBitcoinNodeDatabaseManagerCore(this);
        }

        return _nodeDatabaseManager;
    }

    @Override
    public BlockchainDatabaseManagerCore getBlockchainDatabaseManager() {
        if (_blockchainDatabaseManager == null) {
            _blockchainDatabaseManager = new BlockchainDatabaseManagerCore(this);
        }

        return _blockchainDatabaseManager;
    }

    @Override
    public FullNodeBlockDatabaseManager getBlockDatabaseManager() {
        if (_blockDatabaseManager == null) {
            _blockDatabaseManager = new FullNodeBlockDatabaseManager(this);
        }

        return _blockDatabaseManager;
    }

    @Override
    public FullNodeBlockHeaderDatabaseManager getBlockHeaderDatabaseManager() {
        if (_blockHeaderDatabaseManager == null) {
            _blockHeaderDatabaseManager = new FullNodeBlockHeaderDatabaseManager(this);
        }

        return _blockHeaderDatabaseManager;
    }

    @Override
    public FullNodePendingBlockDatabaseManager getPendingBlockDatabaseManager() {
        if (_pendingBlockDatabaseManager == null) {
            _pendingBlockDatabaseManager = new FullNodePendingBlockDatabaseManager(this);
        }

        return _pendingBlockDatabaseManager;
    }

    @Override
    public FullNodeTransactionDatabaseManager getTransactionDatabaseManager() {
        if (_transactionDatabaseManager == null) {
            _transactionDatabaseManager = new FullNodeTransactionDatabaseManagerCore(this);
        }

        return _transactionDatabaseManager;
    }

    public FullNodeAddressDatabaseManager getAddressDatabaseManager() {
        if (_addressDatabaseManager == null) {
            _addressDatabaseManager = new FullNodeAddressDatabaseManager(this);
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
