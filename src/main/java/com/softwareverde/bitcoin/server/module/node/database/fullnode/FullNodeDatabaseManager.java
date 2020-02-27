package com.softwareverde.bitcoin.server.module.node.database.fullnode;

import com.softwareverde.bitcoin.inflater.MasterInflater;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.module.node.PendingBlockStore;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.address.AddressDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.fullnode.FullNodeBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.fullnode.FullNodeBlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.pending.fullnode.FullNodePendingBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManagerCore;
import com.softwareverde.bitcoin.server.module.node.database.node.fullnode.FullNodeBitcoinNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.node.fullnode.FullNodeBitcoinNodeDatabaseManagerCore;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManagerCore;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.input.UnconfirmedTransactionInputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.output.UnconfirmedTransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.pending.PendingTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.slp.SlpTransactionDatabaseManager;
import com.softwareverde.database.DatabaseException;

public class FullNodeDatabaseManager implements DatabaseManager {
    protected final DatabaseConnection _databaseConnection;
    protected final PendingBlockStore _blockStore;
    protected final MasterInflater _masterInflater;

    protected DatabaseConnectionFactory _databaseConnectionFactory = null;

    protected FullNodeBitcoinNodeDatabaseManager _nodeDatabaseManager;
    protected BlockchainDatabaseManagerCore _blockchainDatabaseManager;
    protected FullNodeBlockDatabaseManager _blockDatabaseManager;
    protected FullNodeBlockHeaderDatabaseManager _blockHeaderDatabaseManager;
    protected FullNodePendingBlockDatabaseManager _pendingBlockDatabaseManager;
    protected AddressDatabaseManager _addressDatabaseManager;
    protected FullNodeTransactionDatabaseManager _transactionDatabaseManager;
    protected UnconfirmedTransactionInputDatabaseManager _unconfirmedTransactionInputDatabaseManager;
    protected UnconfirmedTransactionOutputDatabaseManager _unconfirmedTransactionOutputDatabaseManager;
    protected PendingTransactionDatabaseManager _pendingTransactionDatabaseManager;
    protected SlpTransactionDatabaseManager _slpTransactionDatabaseManager;

    public FullNodeDatabaseManager(final DatabaseConnection databaseConnection, final PendingBlockStore blockStore, final MasterInflater masterInflater) {
        _databaseConnection = databaseConnection;
        _blockStore = blockStore;
        _masterInflater = masterInflater;
    }

    public void setDatabaseConnectionFactory(final DatabaseConnectionFactory databaseConnectionFactory) {
        _databaseConnectionFactory = databaseConnectionFactory;
    }

    public DatabaseConnectionFactory getDatabaseConnectionFactory() {
        return _databaseConnectionFactory;
    }

    @Override
    public DatabaseConnection getDatabaseConnection() {
        return _databaseConnection;
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
            _pendingBlockDatabaseManager = new FullNodePendingBlockDatabaseManager(this, _blockStore);
        }

        return _pendingBlockDatabaseManager;
    }

    @Override
    public FullNodeTransactionDatabaseManager getTransactionDatabaseManager() {
        if (_transactionDatabaseManager == null) {
            _transactionDatabaseManager = new FullNodeTransactionDatabaseManagerCore(this, _blockStore, _masterInflater);
        }

        return _transactionDatabaseManager;
    }

    public AddressDatabaseManager getAddressDatabaseManager() {
        if (_addressDatabaseManager == null) {
            // _addressDatabaseManager = new AddressDatabaseManager(this);
        }

        return _addressDatabaseManager;
    }

    public UnconfirmedTransactionInputDatabaseManager getUnconfirmedTransactionInputDatabaseManager() {
        if (_unconfirmedTransactionInputDatabaseManager == null) {
            _unconfirmedTransactionInputDatabaseManager = new UnconfirmedTransactionInputDatabaseManager(this);
        }

        return _unconfirmedTransactionInputDatabaseManager;
    }

    public UnconfirmedTransactionOutputDatabaseManager getUnconfirmedTransactionOutputDatabaseManager() {
        if (_unconfirmedTransactionOutputDatabaseManager == null) {
            _unconfirmedTransactionOutputDatabaseManager = new UnconfirmedTransactionOutputDatabaseManager(this);
        }

        return _unconfirmedTransactionOutputDatabaseManager;
    }

    public PendingTransactionDatabaseManager getPendingTransactionDatabaseManager() {
        if (_pendingTransactionDatabaseManager == null) {
            _pendingTransactionDatabaseManager = new PendingTransactionDatabaseManager(this);
        }

        return _pendingTransactionDatabaseManager;
    }

    public SlpTransactionDatabaseManager getSlpTransactionDatabaseManager() {
        if (_slpTransactionDatabaseManager == null) {
            // _slpTransactionDatabaseManager = new SlpTransactionDatabaseManager(this);
        }

        return _slpTransactionDatabaseManager;
    }

    @Override
    public void close() throws DatabaseException {
        _databaseConnection.close();
    }
}
