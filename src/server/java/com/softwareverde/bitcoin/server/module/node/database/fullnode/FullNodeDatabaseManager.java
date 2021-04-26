package com.softwareverde.bitcoin.server.module.node.database.fullnode;

import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.inflater.MasterInflater;
import com.softwareverde.bitcoin.server.configuration.CheckpointConfiguration;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.fullnode.FullNodeBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.fullnode.BlockHeaderDatabaseManagerCore;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManagerCore;
import com.softwareverde.bitcoin.server.module.node.database.indexer.BlockchainIndexerDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.indexer.BlockchainIndexerDatabaseManagerCore;
import com.softwareverde.bitcoin.server.module.node.database.node.fullnode.FullNodeBitcoinNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.node.fullnode.FullNodeBitcoinNodeDatabaseManagerCore;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManagerCore;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.input.UnconfirmedTransactionInputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.output.UnconfirmedTransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputJvmManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.pending.PendingTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.slp.SlpTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.slp.SlpTransactionDatabaseManagerCore;
import com.softwareverde.bitcoin.server.module.node.store.PendingBlockStore;
import com.softwareverde.database.DatabaseException;

public class FullNodeDatabaseManager implements DatabaseManager {
    protected final DatabaseConnection _databaseConnection;
    protected final Integer _maxQueryBatchSize;
    protected final PendingBlockStore _blockStore;
    protected final MasterInflater _masterInflater;
    protected final Long _maxUtxoCount;
    protected final Float _utxoPurgePercent;
    protected final CheckpointConfiguration _checkpointConfiguration;

    protected FullNodeBitcoinNodeDatabaseManager _nodeDatabaseManager;
    protected BlockchainDatabaseManagerCore _blockchainDatabaseManager;
    protected FullNodeBlockDatabaseManager _blockDatabaseManager;
    protected BlockHeaderDatabaseManagerCore _blockHeaderDatabaseManager;
    protected BlockchainIndexerDatabaseManager _blockchainIndexerDatabaseManager;
    protected FullNodeTransactionDatabaseManager _transactionDatabaseManager;
    protected UnconfirmedTransactionInputDatabaseManager _unconfirmedTransactionInputDatabaseManager;
    protected UnconfirmedTransactionOutputDatabaseManager _unconfirmedTransactionOutputDatabaseManager;
    protected PendingTransactionDatabaseManager _pendingTransactionDatabaseManager;
    protected SlpTransactionDatabaseManager _slpTransactionDatabaseManager;
    protected UnspentTransactionOutputDatabaseManager _unspentTransactionOutputDatabaseManager;

    public FullNodeDatabaseManager(final DatabaseConnection databaseConnection, final Integer maxQueryBatchSize, final PendingBlockStore blockStore, final MasterInflater masterInflater, final CheckpointConfiguration checkpointConfiguration) {
        this(databaseConnection, maxQueryBatchSize, blockStore, masterInflater, checkpointConfiguration, UnspentTransactionOutputDatabaseManager.DEFAULT_MAX_UTXO_CACHE_COUNT, UnspentTransactionOutputDatabaseManager.DEFAULT_PURGE_PERCENT);
    }

    public FullNodeDatabaseManager(final DatabaseConnection databaseConnection, final Integer maxQueryBatchSize, final PendingBlockStore blockStore, final MasterInflater masterInflater, final CheckpointConfiguration checkpointConfiguration, final Long maxUtxoCount, final Float utxoPurgePercent) {
        _databaseConnection = databaseConnection;
        _maxQueryBatchSize = maxQueryBatchSize;
        _blockStore = blockStore;
        _masterInflater = masterInflater;
        _maxUtxoCount = maxUtxoCount;
        _utxoPurgePercent = utxoPurgePercent;
        _checkpointConfiguration = checkpointConfiguration;
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
    public BlockchainDatabaseManager getBlockchainDatabaseManager() {
        if (_blockchainDatabaseManager == null) {
            _blockchainDatabaseManager = new BlockchainDatabaseManagerCore(this);
        }

        return _blockchainDatabaseManager;
    }

    @Override
    public FullNodeBlockDatabaseManager getBlockDatabaseManager() {
        if (_blockDatabaseManager == null) {
            _blockDatabaseManager = new FullNodeBlockDatabaseManager(this, _blockStore);
        }

        return _blockDatabaseManager;
    }

    @Override
    public BlockHeaderDatabaseManager getBlockHeaderDatabaseManager() {
        if (_blockHeaderDatabaseManager == null) {
            _blockHeaderDatabaseManager = new BlockHeaderDatabaseManagerCore(this, _checkpointConfiguration);
        }

        return _blockHeaderDatabaseManager;
    }

    @Override
    public FullNodeTransactionDatabaseManager getTransactionDatabaseManager() {
        if (_transactionDatabaseManager == null) {
            _transactionDatabaseManager = new FullNodeTransactionDatabaseManagerCore(this, _blockStore, _masterInflater);
        }

        return _transactionDatabaseManager;
    }

    @Override
    public Integer getMaxQueryBatchSize() {
        return _maxQueryBatchSize;
    }

    public BlockchainIndexerDatabaseManager getBlockchainIndexerDatabaseManager() {
        if (_blockchainIndexerDatabaseManager == null) {
            final AddressInflater addressInflater = _masterInflater.getAddressInflater();
            _blockchainIndexerDatabaseManager = new BlockchainIndexerDatabaseManagerCore(addressInflater, this);
        }

        return _blockchainIndexerDatabaseManager;
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
            _slpTransactionDatabaseManager = new SlpTransactionDatabaseManagerCore(this);
        }

        return _slpTransactionDatabaseManager;
    }

    public UnspentTransactionOutputDatabaseManager getUnspentTransactionOutputDatabaseManager() {
        if (_unspentTransactionOutputDatabaseManager == null) {
            _unspentTransactionOutputDatabaseManager = new UnspentTransactionOutputJvmManager(_maxUtxoCount, _utxoPurgePercent, this, _blockStore, _masterInflater);
        }

        return _unspentTransactionOutputDatabaseManager;
    }

    @Override
    public void close() throws DatabaseException {
        _databaseConnection.close();
    }
}
