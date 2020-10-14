package com.softwareverde.bitcoin.server.module.node.database.fullnode;

import com.softwareverde.bitcoin.inflater.MasterInflater;
import com.softwareverde.bitcoin.server.configuration.CheckpointConfiguration;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.store.PendingBlockStore;
import com.softwareverde.database.DatabaseException;

public class FullNodeDatabaseManagerFactory implements DatabaseManagerFactory {
    protected final DatabaseConnectionFactory _databaseConnectionFactory;
    protected final Integer _maxQueryBatchSize;
    protected final PendingBlockStore _blockStore;
    protected final MasterInflater _masterInflater;
    protected final CheckpointConfiguration _checkpointConfiguration;
    protected final Long _maxUtxoCount;
    protected final Float _utxoPurgePercent;

    public FullNodeDatabaseManagerFactory(final DatabaseConnectionFactory databaseConnectionFactory, final Integer maxQueryBatchSize, final PendingBlockStore blockStore, final MasterInflater masterInflater, final CheckpointConfiguration checkpointConfiguration) {
        this(databaseConnectionFactory, maxQueryBatchSize, blockStore, masterInflater, checkpointConfiguration, UnspentTransactionOutputDatabaseManager.DEFAULT_MAX_UTXO_CACHE_COUNT, UnspentTransactionOutputDatabaseManager.DEFAULT_PURGE_PERCENT);
    }

    public FullNodeDatabaseManagerFactory(final DatabaseConnectionFactory databaseConnectionFactory, final Integer maxQueryBatchSize, final PendingBlockStore blockStore, final MasterInflater masterInflater, final CheckpointConfiguration checkpointConfiguration, final Long maxUtxoCount, final Float utxoPurgePercent) {
        _databaseConnectionFactory = databaseConnectionFactory;
        _maxQueryBatchSize = maxQueryBatchSize;
        _blockStore = blockStore;
        _masterInflater = masterInflater;
        _maxUtxoCount = maxUtxoCount;
        _utxoPurgePercent = utxoPurgePercent;
        _checkpointConfiguration = checkpointConfiguration;
    }

    @Override
    public FullNodeDatabaseManager newDatabaseManager() throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection();
        return new FullNodeDatabaseManager(databaseConnection, _maxQueryBatchSize, _blockStore, _masterInflater, _checkpointConfiguration, _maxUtxoCount, _utxoPurgePercent);
    }

    @Override
    public DatabaseConnectionFactory getDatabaseConnectionFactory() {
        return _databaseConnectionFactory;
    }

    @Override
    public FullNodeDatabaseManagerFactory newDatabaseManagerFactory(final DatabaseConnectionFactory databaseConnectionFactory) {
        return new FullNodeDatabaseManagerFactory(databaseConnectionFactory, _maxQueryBatchSize, _blockStore, _masterInflater, _checkpointConfiguration, _maxUtxoCount, _utxoPurgePercent);
    }

    @Override
    public Integer getMaxQueryBatchSize() {
        return _maxQueryBatchSize;
    }
}
