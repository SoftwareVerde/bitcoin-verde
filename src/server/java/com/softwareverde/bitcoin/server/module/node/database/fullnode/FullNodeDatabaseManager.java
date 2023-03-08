package com.softwareverde.bitcoin.server.module.node.database.fullnode;

import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentManager;
import com.softwareverde.bitcoin.inflater.MasterInflater;
import com.softwareverde.bitcoin.server.configuration.CheckpointConfiguration;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.module.node.database.BlockchainCacheReference;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockchainCache;
import com.softwareverde.bitcoin.server.module.node.database.block.MutableBlockchainCache;
import com.softwareverde.bitcoin.server.module.node.database.block.fullnode.FullNodeBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManagerCore;
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
import com.softwareverde.bitcoin.server.module.node.database.utxo.UtxoCommitmentDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.store.PendingBlockStore;
import com.softwareverde.bitcoin.server.module.node.store.UtxoCommitmentStore;
import com.softwareverde.bitcoin.server.module.node.utxo.UtxoCommitmentManagerCore;
import com.softwareverde.bitcoin.server.properties.PropertiesStore;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.logging.Logger;

public class FullNodeDatabaseManager implements DatabaseManager {
    protected final DatabaseConnection _databaseConnection;
    protected final PropertiesStore _propertiesStore;
    protected final Integer _maxQueryBatchSize;
    protected final PendingBlockStore _blockStore;
    protected final MasterInflater _masterInflater;
    protected final Long _maxUtxoCount;
    protected final Float _utxoPurgePercent;
    protected final CheckpointConfiguration _checkpointConfiguration;
    protected final UtxoCommitmentStore _utxoCommitmentStore;

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
    protected UtxoCommitmentDatabaseManager _utxoCommitmentDatabaseManager;
    protected UtxoCommitmentManager _utxoCommitmentManager;

    protected final MutableBlockchainCache _globalBlockchainCache;
    protected final BlockchainCacheReference _blockchainCacheChildReference;
    protected Boolean _cacheWasMutated = false;
    protected MutableBlockchainCache _localBlockchainCache = null;
    protected Boolean _hasDatabaseTransaction = false;
    protected Boolean _localBlockchainCacheWasCreatedWithBlockHeaderLock = false;

    protected Integer _getVersion() {
        if (_globalBlockchainCache == null) { return null; }
        return _globalBlockchainCache.getVersion();
    }

    public FullNodeDatabaseManager(final DatabaseConnection databaseConnection, final Integer maxQueryBatchSize, final PropertiesStore propertiesStore, final PendingBlockStore blockStore, final UtxoCommitmentStore utxoCommitmentStore, final MasterInflater masterInflater, final CheckpointConfiguration checkpointConfiguration, final MutableBlockchainCache blockchainCacheManager) {
        this(databaseConnection, maxQueryBatchSize, propertiesStore, blockStore, utxoCommitmentStore, masterInflater, checkpointConfiguration, UnspentTransactionOutputDatabaseManager.DEFAULT_MAX_UTXO_CACHE_COUNT, UnspentTransactionOutputDatabaseManager.DEFAULT_PURGE_PERCENT, blockchainCacheManager);
    }

    public FullNodeDatabaseManager(final DatabaseConnection databaseConnection, final Integer maxQueryBatchSize, final PropertiesStore propertiesStore, final PendingBlockStore blockStore, final UtxoCommitmentStore utxoCommitmentStore, final MasterInflater masterInflater, final CheckpointConfiguration checkpointConfiguration, final Long maxUtxoCount, final Float utxoPurgePercent, final MutableBlockchainCache blockchainCache) {
        _databaseConnection = databaseConnection;
        _propertiesStore = propertiesStore;
        _maxQueryBatchSize = maxQueryBatchSize;
        _blockStore = blockStore;
        _masterInflater = masterInflater;
        _maxUtxoCount = maxUtxoCount;
        _utxoPurgePercent = utxoPurgePercent;
        _checkpointConfiguration = checkpointConfiguration;
        _utxoCommitmentStore = utxoCommitmentStore;
        _globalBlockchainCache = blockchainCache;

        _blockchainCacheChildReference = new BlockchainCacheReference() {
            @Override
            public BlockchainCache getBlockchainCache() {
                if (_globalBlockchainCache == null) { return null; }

                final boolean holdsBlockHeaderLock = Thread.holdsLock(BlockHeaderDatabaseManager.MUTEX);
                if ( _cacheWasMutated && (! holdsBlockHeaderLock) ) { // (_cacheWasMutated is reset upon commit/rollback/close)
                    throw new RuntimeException("BlockHeader Lock was released without committing/rollback back cache-changes.");
                }

                if (_localBlockchainCache == null) {
                    _localBlockchainCache = _globalBlockchainCache.newCopyOnWriteCache();
                    _localBlockchainCacheWasCreatedWithBlockHeaderLock = holdsBlockHeaderLock;
                }

                return _localBlockchainCache;
            }

            @Override
            public MutableBlockchainCache getMutableBlockchainCache() {
                if (_globalBlockchainCache == null) { return null; }

                final boolean holdsBlockHeaderLock = Thread.holdsLock(BlockHeaderDatabaseManager.MUTEX);
                if (! holdsBlockHeaderLock) {
                    throw new RuntimeException("Attempting to acquire MutableBlockchainCache without obtaining BlockHeader lock.");
                }

                if (! _hasDatabaseTransaction) {
                    throw new RuntimeException("Requested MutableBlockchainCache outside of a database transaction.");
                }

                if (_localBlockchainCache == null) {
                    _localBlockchainCache = _globalBlockchainCache.newCopyOnWriteCache();
                    _localBlockchainCacheWasCreatedWithBlockHeaderLock = true;
                }
                else if (! _localBlockchainCacheWasCreatedWithBlockHeaderLock) {
                    throw new RuntimeException("Requested MutableBlockchainCache with dirty read risk; BlockHeader lock must be acquired before any (mutable/immutable) requests for BlockchainCache.");
                }

                _cacheWasMutated = true;
                return _localBlockchainCache;
            }
        };
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
            _blockchainDatabaseManager = new BlockchainDatabaseManagerCore(this, _blockchainCacheChildReference);
        }

        return _blockchainDatabaseManager;
    }

    @Override
    public FullNodeBlockDatabaseManager getBlockDatabaseManager() {
        if (_blockDatabaseManager == null) {
            _blockDatabaseManager = new FullNodeBlockDatabaseManager(this, _blockStore, _blockchainCacheChildReference);
        }

        return _blockDatabaseManager;
    }

    @Override
    public BlockHeaderDatabaseManager getBlockHeaderDatabaseManager() {
        if (_blockHeaderDatabaseManager == null) {
            _blockHeaderDatabaseManager = new BlockHeaderDatabaseManagerCore(this, _checkpointConfiguration, _blockchainCacheChildReference);
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

    @Override
    public PropertiesStore getPropertiesStore() {
        return _propertiesStore;
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

    public UtxoCommitmentDatabaseManager getUtxoCommitmentDatabaseManager() {
        if (_utxoCommitmentDatabaseManager == null) {
            _utxoCommitmentDatabaseManager = new UtxoCommitmentDatabaseManager(this);
        }

        return _utxoCommitmentDatabaseManager;
    }

    public UtxoCommitmentManager getUtxoCommitmentManager() {
        if (_utxoCommitmentManager == null) {
            _utxoCommitmentManager = new UtxoCommitmentManagerCore(_databaseConnection, _utxoCommitmentStore);
        }

        return _utxoCommitmentManager;
    }

    @Override
    public void startTransaction() throws DatabaseException {
        if (_hasDatabaseTransaction) {
            Logger.warn("Redundant call to DatabaseManager::startTransaction.", new Exception());
            return;
        }

        TransactionUtil.startTransaction(_databaseConnection);
        _hasDatabaseTransaction = true;
    }

    @Override
    public void commitTransaction() throws DatabaseException {
        if (! _hasDatabaseTransaction) {
            Logger.warn("Attempted to call to DatabaseManager::commitTransaction before calling DatabaseManager::startTransaction.", new Exception());
            return;
        }

        TransactionUtil.commitTransaction(_databaseConnection);
        if (_cacheWasMutated) {
            // NOTE: Neither _blockchainCacheGlobalReference nor _blockchainCacheLocalReference can be null if _cacheWasMutated is true.
            _globalBlockchainCache.applyCache(_localBlockchainCache);
            _cacheWasMutated = false;
            _localBlockchainCache = null;
        }
        _hasDatabaseTransaction = false;
        _localBlockchainCacheWasCreatedWithBlockHeaderLock = false;
    }

    @Override
    public void rollbackTransaction() throws DatabaseException {
        if (! _hasDatabaseTransaction) {
            Logger.warn("Attempted to call to DatabaseManager::rollbackTransaction before calling DatabaseManager::startTransaction.", new Exception());
            return;
        }

        TransactionUtil.rollbackTransaction(_databaseConnection);
        if (_cacheWasMutated) {
            // NOTE: Neither _blockchainCacheGlobalReference nor _blockchainCacheLocalReference can be null if _cacheWasMutated is true.
            _cacheWasMutated = false;
            _localBlockchainCache = null;
        }
        _hasDatabaseTransaction = false;
        _localBlockchainCacheWasCreatedWithBlockHeaderLock = false;
    }

    @Override
    public void close() throws DatabaseException {
        if (_hasDatabaseTransaction) {
            TransactionUtil.rollbackTransaction(_databaseConnection);
            _hasDatabaseTransaction = false;

            Logger.debug("Implicit rollback.", new Exception());
        }

        if (_cacheWasMutated) {
            _cacheWasMutated = false;
            _localBlockchainCache = null;

            Logger.debug("NOTICE: Discarding BlockchainCache changes.", new Exception());
        }
        _localBlockchainCacheWasCreatedWithBlockHeaderLock = false;

        _databaseConnection.close();
    }
}
