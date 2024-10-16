package com.softwareverde.bitcoin.server.module.node.database.fullnode;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegment;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.inflater.MasterInflater;
import com.softwareverde.bitcoin.server.configuration.CheckpointConfiguration;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockMetadata;
import com.softwareverde.bitcoin.server.module.node.database.block.MutableBlockchainCache;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.store.PendingBlockStore;
import com.softwareverde.bitcoin.server.module.node.store.UtxoCommitmentStore;
import com.softwareverde.bitcoin.server.properties.PropertiesStore;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.util.map.Visitor;

import java.util.Map;

public class FullNodeDatabaseManagerFactory implements DatabaseManagerFactory {
    protected final DatabaseConnectionFactory _databaseConnectionFactory;
    protected final Integer _maxQueryBatchSize;
    protected final PropertiesStore _propertiesStore;
    protected final PendingBlockStore _blockStore;
    protected final UtxoCommitmentStore _utxoCommitmentStore;
    protected final MasterInflater _masterInflater;
    protected final CheckpointConfiguration _checkpointConfiguration;
    protected final Long _maxUtxoCount;
    protected final Float _utxoPurgePercent;
    protected MutableBlockchainCache _blockchainCache = null; // Disabled cache unless initialize is called...

    public FullNodeDatabaseManagerFactory(final DatabaseConnectionFactory databaseConnectionFactory, final Integer maxQueryBatchSize, final PropertiesStore propertiesStore, final PendingBlockStore blockStore, final UtxoCommitmentStore utxoCommitmentStore, final MasterInflater masterInflater, final CheckpointConfiguration checkpointConfiguration) {
        this(databaseConnectionFactory, maxQueryBatchSize, propertiesStore, blockStore, utxoCommitmentStore, masterInflater, checkpointConfiguration, UnspentTransactionOutputDatabaseManager.DEFAULT_MAX_UTXO_CACHE_COUNT, UnspentTransactionOutputDatabaseManager.DEFAULT_PURGE_PERCENT);
    }

    public FullNodeDatabaseManagerFactory(final DatabaseConnectionFactory databaseConnectionFactory, final Integer maxQueryBatchSize, final PropertiesStore propertiesStore, final PendingBlockStore blockStore, final UtxoCommitmentStore utxoCommitmentStore, final MasterInflater masterInflater, final CheckpointConfiguration checkpointConfiguration, final Long maxUtxoCount, final Float utxoPurgePercent) {
        _databaseConnectionFactory = databaseConnectionFactory;
        _maxQueryBatchSize = maxQueryBatchSize;
        _propertiesStore = propertiesStore;
        _blockStore = blockStore;
        _utxoCommitmentStore = utxoCommitmentStore;
        _masterInflater = masterInflater;
        _maxUtxoCount = maxUtxoCount;
        _utxoPurgePercent = utxoPurgePercent;
        _checkpointConfiguration = checkpointConfiguration;
    }

    public void initializeCache() throws DatabaseException {
        try (final FullNodeDatabaseManager databaseManager = this.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager(); // Uses the disabled cache...
            final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager(); // Uses the disabled cache...

            final MutableBlockchainCache blockchainCache = new MutableBlockchainCache(850_000);
            blockchainCache.pushVersion();

            blockchainDatabaseManager.visitBlockchainSegments(new Visitor<>() {
                @Override
                public void visit(final BlockchainSegmentId blockchainSegmentId) throws Exception {
                    final BlockchainSegment blockchainSegment = blockchainDatabaseManager.getBlockchainSegment(blockchainSegmentId);
                    blockchainCache.addBlockchainSegment(blockchainSegmentId, blockchainSegment.parentBlockchainSegmentId, blockchainSegment.nestedSetLeft, blockchainSegment.nestedSetRight);
                }
            });

            blockHeaderDatabaseManager.visitBlockHeaders(new Visitor<>() {
                @Override
                public void visit(final BlockId blockId) throws Exception {
                    final BlockMetadata blockMetadata = blockHeaderDatabaseManager.getBlockMetadata(blockId);

                    blockchainCache.addBlock(blockId, blockMetadata.blockHeader, blockMetadata.blockHeight, blockMetadata.chainWork, blockMetadata.medianBlockTime, blockMetadata.hasTransactions);
                    blockchainCache.setBlockchainSegmentId(blockId, blockMetadata.blockchainSegmentId);
                }
            });

            final Map<BlockId, Integer> processCounts = blockHeaderDatabaseManager.getBlockProcessCounts();
            for (final BlockId blockId : processCounts.keySet()) {
                final Integer processCount = processCounts.get(blockId);
                blockchainCache.incrementProcessCount(blockId, processCount);
            }

            blockchainCache.applyVersion();
            _blockchainCache = blockchainCache;
        }

    }

    @Override
    public FullNodeDatabaseManager newDatabaseManager() throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection();
        return new FullNodeDatabaseManager(databaseConnection, _maxQueryBatchSize, _propertiesStore, _blockStore, _utxoCommitmentStore, _masterInflater, _checkpointConfiguration, _maxUtxoCount, _utxoPurgePercent, _blockchainCache);
    }

    @Override
    public DatabaseConnectionFactory getDatabaseConnectionFactory() {
        return _databaseConnectionFactory;
    }

    @Override
    public FullNodeDatabaseManagerFactory newDatabaseManagerFactory(final DatabaseConnectionFactory databaseConnectionFactory) {
        return new FullNodeDatabaseManagerFactory(databaseConnectionFactory, _maxQueryBatchSize, _propertiesStore, _blockStore, _utxoCommitmentStore, _masterInflater, _checkpointConfiguration, _maxUtxoCount, _utxoPurgePercent);
    }

    @Override
    public Integer getMaxQueryBatchSize() {
        return _maxQueryBatchSize;
    }
}
