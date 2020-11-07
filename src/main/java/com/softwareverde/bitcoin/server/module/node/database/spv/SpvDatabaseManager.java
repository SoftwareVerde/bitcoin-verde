package com.softwareverde.bitcoin.server.module.node.database.spv;

import com.softwareverde.bitcoin.server.configuration.CheckpointConfiguration;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.fullnode.FullNodeBlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.spv.SpvBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManagerCore;
import com.softwareverde.bitcoin.server.module.node.database.node.spv.SpvBitcoinNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.spv.SpvTransactionDatabaseManager;
import com.softwareverde.database.DatabaseException;

public class SpvDatabaseManager implements DatabaseManager {
    protected final DatabaseConnection _databaseConnection;
    protected final Integer _maxQueryBatchSize;
    protected final CheckpointConfiguration _checkpointConfiguration;

    protected SpvBitcoinNodeDatabaseManager _nodeDatabaseManager;
    protected BlockchainDatabaseManagerCore _blockchainDatabaseManager;
    protected SpvBlockDatabaseManager _blockDatabaseManager;
    protected FullNodeBlockHeaderDatabaseManager _blockHeaderDatabaseManager;
    protected SpvTransactionDatabaseManager _transactionDatabaseManager;

    public SpvDatabaseManager(final DatabaseConnection databaseConnection, final Integer maxQueryBatchSize, final CheckpointConfiguration checkpointConfiguration) {
        _databaseConnection = databaseConnection;
        _maxQueryBatchSize = maxQueryBatchSize;
        _checkpointConfiguration = checkpointConfiguration;
    }

    @Override
    public DatabaseConnection getDatabaseConnection() {
        return _databaseConnection;
    }

    @Override
    public SpvBitcoinNodeDatabaseManager getNodeDatabaseManager() {
        if (_nodeDatabaseManager == null) {
            _nodeDatabaseManager = new SpvBitcoinNodeDatabaseManager(this);
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
    public SpvBlockDatabaseManager getBlockDatabaseManager() {
        if (_blockDatabaseManager == null) {
            _blockDatabaseManager = new SpvBlockDatabaseManager(this);
        }

        return _blockDatabaseManager;
    }

    @Override
    public FullNodeBlockHeaderDatabaseManager getBlockHeaderDatabaseManager() {
        if (_blockHeaderDatabaseManager == null) {
            _blockHeaderDatabaseManager = new FullNodeBlockHeaderDatabaseManager(this, _checkpointConfiguration);
        }

        return _blockHeaderDatabaseManager;
    }

    @Override
    public SpvTransactionDatabaseManager getTransactionDatabaseManager() {
        if (_transactionDatabaseManager == null) {
            _transactionDatabaseManager = new SpvTransactionDatabaseManager(this);
        }

        return _transactionDatabaseManager;
    }

    @Override
    public Integer getMaxQueryBatchSize() {
        return _maxQueryBatchSize;
    }

    @Override
    public void close() throws DatabaseException {
        _databaseConnection.close();
    }
}
