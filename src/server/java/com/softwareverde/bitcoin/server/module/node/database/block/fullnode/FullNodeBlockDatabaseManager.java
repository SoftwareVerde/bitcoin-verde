package com.softwareverde.bitcoin.server.module.node.database.block.fullnode;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MutableMedianBlockTime;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.query.BatchedInsertQuery;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.module.node.database.BlockchainCacheReference;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockchainCache;
import com.softwareverde.bitcoin.server.module.node.database.block.MutableBlockchainCache;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.MedianBlockTimeDatabaseManagerUtil;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.store.BlockStore;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.util.bytearray.CompactVariableLengthInteger;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.MilliTimer;

public class FullNodeBlockDatabaseManager implements BlockDatabaseManager {
    protected final BlockchainCacheReference _blockchainCacheReference;
    protected final FullNodeDatabaseManager _databaseManager;
    protected final BlockStore _blockStore;

    protected void _associateTransactionToBlock(final TransactionId transactionId, final Long diskOffset, final BlockId blockId, final MutableBlockchainCache blockchainCache) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        synchronized (BLOCK_TRANSACTIONS_WRITE_MUTEX) {
            final Integer currentTransactionCount = _getTransactionCount(blockId);
            databaseConnection.executeSql(
                new Query("INSERT INTO block_transactions (block_id, transaction_id, disk_offset, `index`) VALUES (?, ?, ?, ?)")
                    .setParameter(blockId)
                    .setParameter(transactionId)
                    .setParameter(diskOffset)
                    .setParameter(currentTransactionCount)
            );

            databaseConnection.executeSql(
                new Query("UPDATE blocks SET has_transactions = 1 WHERE id = ?")
                    .setParameter(blockId)
            );
        }

        if (blockchainCache != null) {
            blockchainCache.setHasTransactions(blockId);
        }
    }


    protected void _associateTransactionsToBlock(final List<TransactionId> transactionIds, final List<Long> diskOffsets, final BlockId blockId, final MutableBlockchainCache blockchainCache) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        synchronized (BLOCK_TRANSACTIONS_WRITE_MUTEX) {
            final BatchedInsertQuery batchedInsertQuery = new BatchedInsertQuery("INSERT INTO block_transactions (block_id, transaction_id, disk_offset, `index`) VALUES (?, ?, ?, ?)");
            int sortOrder = 0;
            for (final TransactionId transactionId : transactionIds) {
                batchedInsertQuery.setParameter(blockId);
                batchedInsertQuery.setParameter(transactionId);
                batchedInsertQuery.setParameter(diskOffsets.get(sortOrder));
                batchedInsertQuery.setParameter(sortOrder);
                sortOrder += 1;
            }

            databaseConnection.executeSql(batchedInsertQuery);

            databaseConnection.executeSql(
                new Query("UPDATE blocks SET has_transactions = 1 WHERE id = ?")
                    .setParameter(blockId)
            );
        }

        if (blockchainCache != null) {
            blockchainCache.setHasTransactions(blockId);
        }
    }

    protected List<TransactionId> _storeBlockTransactions(final BlockId blockId, final Block block, final DatabaseConnectionFactory databaseConnectionFactory, final Integer maxThreadCount, final MutableBlockchainCache blockchainCache) throws DatabaseException {
        final FullNodeTransactionDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionDatabaseManager();

        final List<Transaction> transactions = block.getTransactions();

        long diskOffset = BlockHeaderInflater.BLOCK_HEADER_BYTE_COUNT;
        diskOffset += CompactVariableLengthInteger.variableLengthIntegerToBytes(transactions.getCount()).getByteCount();

        final MutableList<Long> diskOffsets = new MutableArrayList<>(transactions.getCount());
        for (final Transaction transaction : transactions) {
            diskOffsets.add(diskOffset);
            diskOffset += transaction.getByteCount();
        }

        final MilliTimer storeBlockTimer = new MilliTimer();
        final MilliTimer associateTransactionsTimer = new MilliTimer();

        final List<TransactionId> transactionIds;
        storeBlockTimer.start();
        {
            transactionIds = transactionDatabaseManager.storeTransactionHashes(transactions, databaseConnectionFactory, maxThreadCount);
            if (transactionIds == null) { throw new DatabaseException("Unable to store block transactions."); }

            associateTransactionsTimer.start();
            _associateTransactionsToBlock(transactionIds, diskOffsets, blockId, blockchainCache);
            associateTransactionsTimer.stop();
            Logger.debug("AssociateTransactions: " + associateTransactionsTimer.getMillisecondsElapsed() + "ms");
        }
        storeBlockTimer.stop();
        Logger.debug("StoreBlockDuration: " + storeBlockTimer.getMillisecondsElapsed() + "ms");
        return transactionIds;
    }

    protected Boolean _hasTransactions(final BlockId blockId, final BlockchainCache blockchainCache) throws DatabaseException {
        if (blockchainCache != null) {
            final Boolean hasTransactions = blockchainCache.hasTransactions(blockId);
            if (hasTransactions != null) {
                return hasTransactions;
            }
        }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id FROM blocks WHERE id = ? AND has_transactions = 1")
                .setParameter(blockId)
        );
        return (! rows.isEmpty());
    }

    protected Integer _getTransactionCount(final BlockId blockId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT COUNT(*) AS transaction_count FROM block_transactions WHERE block_id = ?")
                .setParameter(blockId)
        );
        if (rows.isEmpty()) { return 0; }

        final Row row = rows.get(0);
        return row.getInteger("transaction_count");
    }

    protected List<TransactionId> _getTransactionIds(final BlockId blockId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, transaction_id FROM block_transactions WHERE block_id = ? ORDER BY `index` ASC")
                .setParameter(blockId)
        );

        final ImmutableListBuilder<TransactionId> listBuilder = new ImmutableListBuilder<>(rows.size());
        for (final Row row : rows) {
            final TransactionId transactionId = TransactionId.wrap(row.getLong("transaction_id"));
            listBuilder.add(transactionId);
        }
        return listBuilder.build();
    }

    protected BlockId _getHeadBlockId(final BlockchainCache blockchainCache) throws DatabaseException {
        if (blockchainCache != null) {
            final BlockId blockId = blockchainCache.getHeadBlockId();
            if (blockId != null) {
                return blockId;
            }
        }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id FROM head_block")
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return BlockId.wrap(row.getLong("id"));
    }

    protected Sha256Hash _getHeadBlockHash(final BlockchainCache blockchainCache) throws DatabaseException {
        if (blockchainCache != null) {
            final BlockId blockId = blockchainCache.getHeadBlockId();
            if (blockId != null) {
                final BlockHeader blockHeader = blockchainCache.getBlockHeader(blockId);
                if (blockHeader != null) {
                    return blockHeader.getHash(); // NOTE: Hash is cached internally.
                }
            }
        }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, hash FROM head_block")
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return Sha256Hash.wrap(row.getBytes("hash"));
    }

    protected MutableBlock _getBlock(final BlockId blockId, final BlockchainCache blockchainCache) throws DatabaseException {
        if (! _hasTransactions(blockId, blockchainCache)) { return null; }

        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();
        final Sha256Hash blockHash = blockHeaderDatabaseManager.getBlockHash(blockId);
        final Long blockHeight = blockHeaderDatabaseManager.getBlockHeight(blockId);
        return _blockStore.getBlock(blockHash, blockHeight);
    }

    public FullNodeBlockDatabaseManager(final FullNodeDatabaseManager databaseManager, final BlockStore blockStore, final BlockchainCacheReference blockchainCacheReference) {
        _databaseManager = databaseManager;
        _blockStore = blockStore;
        _blockchainCacheReference = blockchainCacheReference;
    }

    public MutableBlock getBlock(final BlockId blockId) throws DatabaseException {
        final BlockchainCache blockchainCache = _blockchainCacheReference.getBlockchainCache();
        return _getBlock(blockId, blockchainCache);
    }

    /**
     * Inserts the Block (and BlockHeader if it does not exist) (including its transactions) into the database.
     *  If the BlockHeader has already been stored, this will update the existing BlockHeader.
     *  Transactions inserted on this chain are assumed to be a part of the parent's chain if the BlockHeader did not exist.
     */
    public BlockId storeBlock(final Block block) throws DatabaseException { return this.storeBlock(block, null); }
    public BlockId storeBlock(final Block block, final MutableList<TransactionId> returnedTransactionIds) throws DatabaseException { return this.storeBlock(block, returnedTransactionIds, null, null); }
    public BlockId storeBlock(final Block block, final MutableList<TransactionId> returnedTransactionIds, final DatabaseConnectionFactory databaseConnectionFactory, final Integer maxThreadCount) throws DatabaseException {
        if (! Thread.holdsLock(BlockHeaderDatabaseManager.MUTEX)) { throw new RuntimeException("Attempting to storeBlock without obtaining lock."); }

        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();
        final BlockchainDatabaseManager blockchainDatabaseManager = _databaseManager.getBlockchainDatabaseManager();

        final Sha256Hash blockHash = block.getHash();
        final BlockId blockId;
        {
            final BlockId existingBlockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
            if (existingBlockId == null) {
                blockId = blockHeaderDatabaseManager.insertBlockHeader(block);
                blockchainDatabaseManager.updateBlockchainsForNewBlock(blockId);
            }
            else {
                blockId = existingBlockId;
            }
        }

        final MutableBlockchainCache blockchainCache = _blockchainCacheReference.getMutableBlockchainCache();
        final List<TransactionId> transactionIds = _storeBlockTransactions(blockId, block, databaseConnectionFactory, maxThreadCount, blockchainCache);
        if (returnedTransactionIds != null) {
            returnedTransactionIds.addAll(transactionIds);
        }

        final Long blockHeight = blockHeaderDatabaseManager.getBlockHeight(blockId);
        _blockStore.storeBlock(block, blockHeight);

        return blockId;
    }

    public List<TransactionId> storeBlockTransactions(final Block block) throws DatabaseException { return this.storeBlockTransactions(block, null, null); }
    public List<TransactionId> storeBlockTransactions(final Block block, final DatabaseConnectionFactory databaseConnectionFactory, final Integer maxThreadCount) throws DatabaseException {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();

        final Sha256Hash blockHash = block.getHash();
        final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
        if (blockId == null) {
            Logger.warn("Attempting to insert transactions without BlockHeader stored: "+ blockHash);
            return null;
        }

        final MutableBlockchainCache blockchainCache = _blockchainCacheReference.getMutableBlockchainCache();
        final List<TransactionId> transactionIds = _storeBlockTransactions(blockId, block, databaseConnectionFactory, maxThreadCount, blockchainCache);

        final Long blockHeight = blockHeaderDatabaseManager.getBlockHeight(blockId);
        _blockStore.storeBlock(block, blockHeight);

        return transactionIds;
    }

    /**
     * Inserts the Block (including its transactions) into the database.
     *  If the BlockHeader has already been stored, this function will throw a DatabaseException.
     *  Transactions inserted on this chain are assumed to be a part of the parent's chain.
     */
    public BlockId insertBlock(final Block block) throws DatabaseException { return this.insertBlock(block, null); }
    public BlockId insertBlock(final Block block, final MutableList<TransactionId> returnedTransactionIds) throws DatabaseException { return this.insertBlock(block, returnedTransactionIds, null, null); }
    public BlockId insertBlock(final Block block, final MutableList<TransactionId> returnedTransactionIds, final DatabaseConnectionFactory databaseConnectionFactory, final Integer maxThreadCount) throws DatabaseException {
        if (! Thread.holdsLock(BlockHeaderDatabaseManager.MUTEX)) { throw new RuntimeException("Attempting to insertBlock without obtaining lock."); }

        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();
        final BlockchainDatabaseManager blockchainDatabaseManager = _databaseManager.getBlockchainDatabaseManager();

        final BlockId blockId = blockHeaderDatabaseManager.insertBlockHeader(block);
        if (blockId == null) { return null; }

        blockchainDatabaseManager.updateBlockchainsForNewBlock(blockId);

        final MutableBlockchainCache blockchainCache = _blockchainCacheReference.getMutableBlockchainCache();
        final List<TransactionId> transactionIds = _storeBlockTransactions(blockId, block, databaseConnectionFactory, maxThreadCount, blockchainCache);
        if (returnedTransactionIds != null) {
            returnedTransactionIds.addAll(transactionIds);
        }

        if (_blockStore != null) {
            final Long blockHeight = blockHeaderDatabaseManager.getBlockHeight(blockId);
            _blockStore.storeBlock(block, blockHeight);
        }

        return blockId;
    }

    /**
     * Sets all blocks below the provided blockHeight as processed.
     *  This function is only intended to be used only for post-Utxo Commitment import.
     */
    public void setHasTransactionsForBlocksUpToHeight(final Long blockHeight) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        databaseConnection.executeSql(
            new Query("UPDATE blocks SET has_transactions = 1 WHERE block_height <= ?")
                .setParameter(blockHeight)
        );

        final MutableBlockchainCache blockchainCache = _blockchainCacheReference.getMutableBlockchainCache();
        if (blockchainCache != null) {
            blockchainCache.setHasTransactionsForBlocksUpToHeight(blockHeight);
        }
    }

    /**
     * Returns the Sha256Hash of the block that has the most PoW that has been validated (i.e. has transactions).
     */
    @Override
    public Sha256Hash getHeadBlockHash() throws DatabaseException {
        final BlockchainCache blockchainCache = _blockchainCacheReference.getBlockchainCache();
        return _getHeadBlockHash(blockchainCache);
    }

    /**
     * Returns the BlockId of the block that has the most PoW that has been validated (i.e. has transactions).
     */
    @Override
    public BlockId getHeadBlockId() throws DatabaseException {
        final BlockchainCache blockchainCache = _blockchainCacheReference.getBlockchainCache();
        return _getHeadBlockId(blockchainCache);
    }

    /**
     * Returns the BlockId of the block that has the tallest block-height that has been validated (i.e. has transactions) within the
     *  BlockchainSegment with the provided blockchainSegmentId. Parent/Ancestor blockchainSegments are not considered.
     *  NOTE: PoW (ChainWork) vs BlockHeight is equivalent when on the same BlockchainSegment.
     */
    public BlockId getHeadBlockIdOfBlockchainSegment(final BlockchainSegmentId blockchainSegmentId) throws DatabaseException {
        final BlockchainCache blockchainCache = _blockchainCacheReference.getBlockchainCache();
        if (blockchainCache != null) {
            if (blockchainSegmentId != null) {
                final BlockId headBlockId = blockchainCache.getHeadBlockIdOfBlockchainSegment(blockchainSegmentId, true);
                if (headBlockId != null) {
                    return headBlockId;
                }
            }
        }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id FROM blocks WHERE blockchain_segment_id = ? AND has_transactions = 1 ORDER BY block_height DESC LIMIT 1")
                .setParameter(blockchainSegmentId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final Long blockId = row.getLong("id");
        return BlockId.wrap(blockId);
    }

    /**
     * Returns true if the BlockHeader and its Transactions have been downloaded and verified.
     */
    @Override
    public Boolean hasTransactions(final Sha256Hash blockHash) throws DatabaseException {
        final BlockchainCache blockchainCache = _blockchainCacheReference.getBlockchainCache();
        if (blockchainCache != null) {
            final BlockId blockId = blockchainCache.getBlockId(blockHash);
            if (blockId != null) {
                final Boolean hasTransactions = blockchainCache.hasTransactions(blockId);
                if (hasTransactions != null) {
                    return hasTransactions;
                }
            }
        }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id FROM blocks WHERE hash = ? AND has_transactions = 1")
                .setParameter(blockHash)
        );
        return (! rows.isEmpty());
    }

    @Override
    public Boolean hasTransactions(final BlockId blockId) throws DatabaseException {
        final BlockchainCache blockchainCache = _blockchainCacheReference.getBlockchainCache();
        return _hasTransactions(blockId, blockchainCache);
    }

    @Override
    public List<TransactionId> getTransactionIds(final BlockId blockId) throws DatabaseException {
        return _getTransactionIds(blockId);
    }

    public Integer getTransactionIndex(final BlockId blockId, final TransactionId transactionId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT `index` FROM block_transactions WHERE block_id = ? AND transaction_id = ?")
                .setParameter(blockId)
                .setParameter(transactionId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return row.getInteger("index");
    }

    /**
     * Initializes a Mutable MedianBlockTime using most recent fully-validated Blocks.
     *  The MedianBlockTime returned includes the head Block's timestamp, and is therefore the MedianTimePast value for the next mined Block.
     */
    @Override
    public MutableMedianBlockTime calculateMedianBlockTime() throws DatabaseException {
        final BlockchainCache blockchainCache = _blockchainCacheReference.getBlockchainCache();
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();
        final Sha256Hash blockHash = Util.coalesce(_getHeadBlockHash(blockchainCache), BlockHeader.GENESIS_BLOCK_HASH);
        return MedianBlockTimeDatabaseManagerUtil.calculateMedianBlockTime(blockHeaderDatabaseManager, blockHash);
    }

    @Override
    public Integer getTransactionCount(final BlockId blockId) throws DatabaseException {
        return _getTransactionCount(blockId);
    }
}
