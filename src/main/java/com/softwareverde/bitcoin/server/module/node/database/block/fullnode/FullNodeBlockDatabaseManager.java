package com.softwareverde.bitcoin.server.module.node.database.block.fullnode;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.BatchedInsertQuery;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.MilliTimer;

import java.util.HashMap;
import java.util.Set;
import java.util.TreeSet;

public class FullNodeBlockDatabaseManager implements BlockDatabaseManager {
    protected final FullNodeDatabaseManager _databaseManager;

    protected void _associateTransactionToBlock(final TransactionId transactionId, final BlockId blockId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        synchronized (BLOCK_TRANSACTIONS_WRITE_MUTEX) {
            final Integer currentTransactionCount = _getTransactionCount(blockId);
            databaseConnection.executeSql(
                new Query("INSERT INTO block_transactions (block_id, transaction_id, `index`) VALUES (?, ?, ?)")
                    .setParameter(blockId)
                    .setParameter(transactionId)
                    .setParameter(currentTransactionCount)
            );
        }
    }


    protected void _associateTransactionsToBlock(final List<TransactionId> transactionIds, final BlockId blockId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        synchronized (BLOCK_TRANSACTIONS_WRITE_MUTEX) {
            final BatchedInsertQuery batchedInsertQuery = new BatchedInsertQuery("INSERT INTO block_transactions (block_id, transaction_id, `index`) VALUES (?, ?, ?)");
            int sortOrder = 0;
            for (final TransactionId transactionId : transactionIds) {
                batchedInsertQuery.setParameter(blockId);
                batchedInsertQuery.setParameter(transactionId);
                batchedInsertQuery.setParameter(sortOrder);
                sortOrder += 1;
            }

            databaseConnection.executeSql(batchedInsertQuery);
        }
    }

    protected void _storeBlockTransactions(final BlockId blockId, final List<Transaction> transactions) throws DatabaseException {
        final TransactionDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionDatabaseManager();

        final MilliTimer storeBlockTimer = new MilliTimer();
        final MilliTimer associateTransactionsTimer = new MilliTimer();

        storeBlockTimer.start();
        {
            final List<TransactionId> transactionIds = transactionDatabaseManager.storeTransactions(transactions);
            if (transactionIds == null) { throw new DatabaseException("Unable to store block transactions."); }

            associateTransactionsTimer.start();
            _associateTransactionsToBlock(transactionIds, blockId);
            associateTransactionsTimer.stop();
            Logger.info("AssociateTransactions: " + associateTransactionsTimer.getMillisecondsElapsed() + "ms");
        }
        storeBlockTimer.stop();
        Logger.info("StoreBlockDuration: " + storeBlockTimer.getMillisecondsElapsed() + "ms");
    }

    protected Integer _getTransactionCount(final BlockId blockId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT COUNT(*) AS transaction_count FROM block_transactions WHERE block_id = ?")
                .setParameter(blockId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return row.getInteger("transaction_count");
    }

    protected List<TransactionId> _getTransactionIds(final BlockId blockId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, transaction_id FROM block_transactions WHERE block_id = ? ORDER BY `index` ASC")
                .setParameter(blockId)
        );

        final ImmutableListBuilder<TransactionId> listBuilder = new ImmutableListBuilder<TransactionId>(rows.size());
        for (final Row row : rows) {
            final TransactionId transactionId = TransactionId.wrap(row.getLong("transaction_id"));
            listBuilder.add(transactionId);
        }
        return listBuilder.build();
    }

    protected List<Transaction> _getBlockTransactions(final BlockId blockId) throws DatabaseException {
        final FullNodeTransactionDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionDatabaseManager();

        final List<TransactionId> transactionIds = _getTransactionIds(blockId);

        final ImmutableListBuilder<Transaction> listBuilder = new ImmutableListBuilder<Transaction>(transactionIds.getSize());
        for (final TransactionId transactionId : transactionIds) {
            final Transaction transaction = transactionDatabaseManager.getTransaction(transactionId);
            if (transaction == null) { return null; }

            listBuilder.add(transaction);
        }
        return listBuilder.build();
    }

    protected BlockId _getHeadBlockId() throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT blocks.id, blocks.hash FROM blocks INNER JOIN block_transactions ON block_transactions.block_id = blocks.id ORDER BY blocks.chain_work DESC LIMIT 1")
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return BlockId.wrap(row.getLong("id"));
    }

    protected Sha256Hash _getHeadBlockHash() throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT blocks.id, blocks.hash FROM blocks INNER JOIN block_transactions ON block_transactions.block_id = blocks.id ORDER BY blocks.chain_work DESC LIMIT 1")
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return Sha256Hash.copyOf(row.getBytes("hash"));
    }

    protected MutableBlock _getBlock(final BlockId blockId) throws DatabaseException {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();

        final BlockHeader blockHeader = blockHeaderDatabaseManager.getBlockHeader(blockId);

        if (blockHeader == null) {
            final Sha256Hash blockHash = blockHeaderDatabaseManager.getBlockHash(blockId);
            Logger.warn("Unable to inflate block. BlockId: " + blockId + " Hash: " + blockHash);
            return null;
        }

        final List<Transaction> transactions = _getBlockTransactions(blockId);
        if (transactions == null) {
            Logger.warn("Unable to inflate block: " + blockHeader.getHash());
            return null;
        }

        final MutableBlock block = new MutableBlock(blockHeader, transactions);

        if (! Util.areEqual(blockHeader.getHash(), block.getHash())) {
            Logger.warn("Unable to inflate block: " + blockHeader.getHash());
            return null;
        }

        return block;
    }

    public FullNodeBlockDatabaseManager(final FullNodeDatabaseManager databaseManager) {
        _databaseManager = databaseManager;
    }

    public MutableBlock getBlock(final BlockId blockId) throws DatabaseException {
        return _getBlock(blockId);
    }

    /**
     * Inserts the Block (and BlockHeader if it does not exist) (including its transactions) into the database.
     *  If the BlockHeader has already been stored, this will update the existing BlockHeader.
     *  Transactions inserted on this chain are assumed to be a part of the parent's chain if the BlockHeader did not exist.
     */
    public BlockId storeBlock(final Block block) throws DatabaseException {
        if (! Thread.holdsLock(BlockHeaderDatabaseManager.MUTEX)) { throw new RuntimeException("Attempting to storeBlock without obtaining lock."); }

        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();
        final BlockchainDatabaseManager blockchainDatabaseManager = _databaseManager.getBlockchainDatabaseManager();

        final Sha256Hash blockHash = block.getHash();
        final BlockId existingBlockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);

        final BlockId blockId;
        if (existingBlockId == null) {
            blockId = blockHeaderDatabaseManager.insertBlockHeader(block);
            blockchainDatabaseManager.updateBlockchainsForNewBlock(blockId);
        }
        else {
            blockId = existingBlockId;
        }

        _storeBlockTransactions(blockId, block.getTransactions());

        return blockId;
    }

    public Boolean storeBlockTransactions(final Block block) throws DatabaseException {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();

        final Sha256Hash blockHash = block.getHash();
        final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
        if (blockId == null) {
            Logger.warn("Attempting to insert transactions without BlockHeader stored: "+ blockHash);
            return false;
        }

        _storeBlockTransactions(blockId, block.getTransactions());

        return true;
    }

    /**
     * Inserts the Block (including its transactions) into the database.
     *  If the BlockHeader has already been stored, this function will throw a DatabaseException.
     *  Transactions inserted on this chain are assumed to be a part of the parent's chain.
     */
    public BlockId insertBlock(final Block block) throws DatabaseException {
        if (! Thread.holdsLock(BlockHeaderDatabaseManager.MUTEX)) { throw new RuntimeException("Attempting to insertBlock without obtaining lock."); }

        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();
        final BlockchainDatabaseManager blockchainDatabaseManager = _databaseManager.getBlockchainDatabaseManager();

        final BlockId blockId = blockHeaderDatabaseManager.insertBlockHeader(block);
        if (blockId == null) { return null; }

        blockchainDatabaseManager.updateBlockchainsForNewBlock(blockId);

        _storeBlockTransactions(blockId, block.getTransactions());
        return blockId;
    }

    /**
     * Returns the Sha256Hash of the block that has the tallest block-height that has been fully downloaded (i.e. has transactions).
     */
    @Override
    public Sha256Hash getHeadBlockHash() throws DatabaseException {
        return _getHeadBlockHash();
    }

    /**
     * Returns the BlockId of the block that has the tallest block-height that has been fully downloaded (i.e. has transactions).
     */
    @Override
    public BlockId getHeadBlockId() throws DatabaseException {
        return _getHeadBlockId();
    }

    /**
     * Returns true if the BlockHeader and its Transactions have been downloaded and verified.
     */
    @Override
    public Boolean hasTransactions(final Sha256Hash blockHash) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT blocks.id, blocks.hash FROM blocks INNER JOIN block_transactions ON block_transactions.block_id = blocks.id WHERE blocks.hash = ? GROUP BY blocks.id")
                .setParameter(blockHash)
        );
        return (! rows.isEmpty());
    }

    @Override
    public Boolean hasTransactions(final BlockId blockId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id FROM block_transactions WHERE block_id = ? LIMIT 1")
                .setParameter(blockId)
        );
        return (! rows.isEmpty());
    }

    @Override
    public List<TransactionId> getTransactionIds(final BlockId blockId) throws DatabaseException {
        return _getTransactionIds(blockId);
    }

    @Override
    public Integer getTransactionCount(final BlockId blockId) throws DatabaseException {
        return _getTransactionCount(blockId);
    }

    public void repairBlock(final Block block, final Boolean isTrimModeEnabled) throws DatabaseException {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();
        final FullNodeTransactionDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionDatabaseManager();

        final Sha256Hash blockHash = block.getHash();
        final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
        if (blockId == null) {
            Logger.warn("Block not found: " + blockHash);
            return;
        }

        blockHeaderDatabaseManager.updateBlockHeader(blockId, block);

        final Set<Sha256Hash> updatedTransactions = new TreeSet<Sha256Hash>();
        { // Remove transactions that do not exist in the updated block, and update ones that do not exist...
            final HashMap<Sha256Hash, Transaction> existingTransactionHashes = new HashMap<Sha256Hash, Transaction>(block.getTransactionCount());
            for (final Transaction transaction : block.getTransactions()) {
                existingTransactionHashes.put(transaction.getHash(), transaction);
            }

            final List<TransactionId> transactionIds = _getTransactionIds(blockId);

            for (final TransactionId transactionId : transactionIds) {
                final Sha256Hash transactionHash = transactionDatabaseManager.getTransactionHash(transactionId);

                final boolean transactionExistsInUpdatedBlock = existingTransactionHashes.containsKey(transactionHash);

                if (transactionExistsInUpdatedBlock) {
                    final Transaction transaction = existingTransactionHashes.get(transactionHash);
                    Logger.info("Updating Transaction: " + transactionHash + " Id: " + transactionId);
                    transactionDatabaseManager.updateTransaction(transaction, isTrimModeEnabled);
                    updatedTransactions.add(transactionHash);
                }
                else {
                    Logger.info("Deleting Transaction: " + transactionHash + " Id: " + transactionId);
                    transactionDatabaseManager.deleteTransaction(transactionId);
                }
            }
        }

        for (final Transaction transaction : block.getTransactions()) {
            final Sha256Hash transactionHash = transaction.getHash();
            final boolean transactionHasBeenProcessed = updatedTransactions.contains(transactionHash);
            if (transactionHasBeenProcessed) { continue; }

            Logger.info("Inserting Transaction: " + transactionHash);
            final TransactionId transactionId = transactionDatabaseManager.storeTransaction(transaction);
            if (transactionId == null) { throw new DatabaseException("Error inserting Transaction."); }
        }
    }
}
