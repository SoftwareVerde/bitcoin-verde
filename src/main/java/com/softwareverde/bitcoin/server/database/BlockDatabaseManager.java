package com.softwareverde.bitcoin.server.database;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.io.Logger;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.MilliTimer;

import java.util.HashMap;
import java.util.Set;
import java.util.TreeSet;

public class BlockDatabaseManager {
    protected final MysqlDatabaseConnection _databaseConnection;
    protected final DatabaseManagerCache _databaseManagerCache;


    protected void _storeBlockTransactions(final BlockId blockId, final List<Transaction> transactions) throws DatabaseException {
        final MilliTimer storeBlockTimer = new MilliTimer();
        final MilliTimer associateTransactionsTimer = new MilliTimer();

        final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(_databaseConnection, _databaseManagerCache);

        storeBlockTimer.start();
        {
            final List<TransactionId> transactionIds = transactionDatabaseManager.storeTransactions(transactions);
            if (transactionIds == null) { throw new DatabaseException("Unable to store block transactions."); }

            associateTransactionsTimer.start();
            transactionDatabaseManager.associateTransactionsToBlock(transactionIds, blockId);
            associateTransactionsTimer.stop();
            Logger.log("AssociateTransactions: " + associateTransactionsTimer.getMillisecondsElapsed() + "ms");
        }
        storeBlockTimer.stop();
        Logger.log("StoreBlockDuration: " + storeBlockTimer.getMillisecondsElapsed() + "ms");
    }

    protected List<Transaction> _getBlockTransactions(final BlockId blockId, final Boolean shouldUpdateUnspentOutputCache) throws DatabaseException {
        final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(_databaseConnection, _databaseManagerCache);
        final List<TransactionId> transactionIds = transactionDatabaseManager.getTransactionIds(blockId);

        final ImmutableListBuilder<Transaction> listBuilder = new ImmutableListBuilder<Transaction>(transactionIds.getSize());
        for (final TransactionId transactionId : transactionIds) {
            final Transaction transaction = transactionDatabaseManager.getTransaction(transactionId, shouldUpdateUnspentOutputCache);
            if (transaction == null) { return null; }

            listBuilder.add(transaction);
        }
        return listBuilder.build();
    }

    protected BlockId _getHeadBlockId() throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT blocks.id, blocks.hash FROM blocks INNER JOIN block_transactions ON block_transactions.block_id = blocks.id ORDER BY blocks.block_height DESC LIMIT 1")
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return BlockId.wrap(row.getLong("id"));
    }

    protected Sha256Hash _getHeadBlockHash() throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT blocks.id, blocks.hash FROM blocks INNER JOIN block_transactions ON block_transactions.block_id = blocks.id ORDER BY blocks.block_height DESC LIMIT 1")
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return Sha256Hash.fromHexString(row.getString("hash"));
    }

    protected MutableBlock _getBlock(final BlockId blockId, final Boolean shouldUpdateUnspentOutputCache) throws DatabaseException {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(_databaseConnection, _databaseManagerCache);
        final BlockHeader blockHeader = blockHeaderDatabaseManager.getBlockHeader(blockId);

        if (blockHeader == null) {
            final Sha256Hash blockHash = blockHeaderDatabaseManager.getBlockHash(blockId);
            Logger.log("ERROR: Unable to inflate block. BlockId: " + blockId + " Hash: " + blockHash);
            return null;
        }

        final List<Transaction> transactions = _getBlockTransactions(blockId, shouldUpdateUnspentOutputCache);
        if (transactions == null) {
            Logger.log("ERROR: Unable to inflate block: " + blockHeader.getHash());
            return null;
        }

        final MutableBlock block = new MutableBlock(blockHeader, transactions);

        if (! Util.areEqual(blockHeader.getHash(), block.getHash())) {
            Logger.log("ERROR: Unable to inflate block: " + blockHeader.getHash());
            return null;
        }

        return block;
    }

    public BlockDatabaseManager(final MysqlDatabaseConnection databaseConnection, final DatabaseManagerCache databaseManagerCache) {
        _databaseConnection = databaseConnection;
        _databaseManagerCache = databaseManagerCache;
    }

    public MutableBlock getBlock(final BlockId blockId) throws DatabaseException {
        return _getBlock(blockId, false);
    }

    public MutableBlock getBlock(final BlockId blockId, final Boolean shouldUpdateUnspentOutputCache) throws DatabaseException {
        return _getBlock(blockId, shouldUpdateUnspentOutputCache);
    }

    /**
     * Inserts the Block (and BlockHeader if it does not exist) (including its transactions) into the database.
     *  If the BlockHeader has already been stored, this will update the existing BlockHeader.
     *  Transactions inserted on this chain are assumed to be a part of the parent's chain if the BlockHeader did not exist.
     */
    public BlockId storeBlock(final Block block) throws DatabaseException {
        if (! Thread.holdsLock(BlockHeaderDatabaseManager.MUTEX)) { throw new RuntimeException("Attempting to storeBlock without obtaining lock."); }

        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(_databaseConnection, _databaseManagerCache);

        final Sha256Hash blockHash = block.getHash();
        final BlockId existingBlockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);

        final BlockId blockId;
        if (existingBlockId == null) {
            blockId = blockHeaderDatabaseManager.insertBlockHeader(block);

            final BlockchainDatabaseManager blockchainDatabaseManager = new BlockchainDatabaseManager(_databaseConnection, _databaseManagerCache);
            blockchainDatabaseManager.updateBlockchainsForNewBlock(blockId);
        }
        else {
            blockId = existingBlockId;
        }

        _storeBlockTransactions(blockId, block.getTransactions());

        return blockId;
    }

    public Boolean storeBlockTransactions(final Block block) throws DatabaseException {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(_databaseConnection, _databaseManagerCache);

        final Sha256Hash blockHash = block.getHash();
        final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
        if (blockId == null) {
            Logger.log("Attempting to insert transactions without BlockHeader stored: "+ blockHash);
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

        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(_databaseConnection, _databaseManagerCache);

        final BlockId blockId = blockHeaderDatabaseManager.insertBlockHeader(block);
        if (blockId == null) { return null; }

        final BlockchainDatabaseManager blockchainDatabaseManager = new BlockchainDatabaseManager(_databaseConnection, _databaseManagerCache);
        blockchainDatabaseManager.updateBlockchainsForNewBlock(blockId);

        _storeBlockTransactions(blockId, block.getTransactions());
        return blockId;
    }

    /**
     * Returns the Sha256Hash of the block that has the tallest block-height that has been fully downloaded (i.e. has transactions).
     */
    public Sha256Hash getHeadBlockHash() throws DatabaseException {
        return _getHeadBlockHash();
    }

    /**
     * Returns the BlockId of the block that has the tallest block-height that has been fully downloaded (i.e. has transactions).
     */
    public BlockId getHeadBlockId() throws DatabaseException {
        return _getHeadBlockId();
    }

    /**
     * Returns true if the BlockHeader and its Transactions have been downloaded and verified.
     */
    public Boolean blockHeaderHasTransactions(final Sha256Hash blockHash) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT blocks.id, blocks.hash FROM blocks INNER JOIN block_transactions ON block_transactions.block_id = blocks.id WHERE blocks.hash = ? GROUP BY blocks.id")
                .setParameter(blockHash)
        );
        return (! rows.isEmpty());
    }

    public Boolean hasTransactions(final BlockId blockId) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM block_transactions WHERE block_id = ? GROUP BY block_id")
                .setParameter(blockId)
        );
        return (! rows.isEmpty());
    }

    public void repairBlock(final Block block) throws DatabaseException {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(_databaseConnection, _databaseManagerCache);
        final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(_databaseConnection, _databaseManagerCache);

        final Sha256Hash blockHash = block.getHash();
        final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
        if (blockId == null) {
            Logger.log("Block not found: " + blockHash);
            return;
        }

        blockHeaderDatabaseManager.updateBlockHeader(blockId, block);

        final Set<Sha256Hash> updatedTransactions = new TreeSet<Sha256Hash>();
        { // Remove transactions that do not exist in the updated block, and update ones that do not exist...
            final HashMap<Sha256Hash, Transaction> existingTransactionHashes = new HashMap<Sha256Hash, Transaction>(block.getTransactionCount());
            for (final Transaction transaction : block.getTransactions()) {
                existingTransactionHashes.put(transaction.getHash(), transaction);
            }

            final List<TransactionId> transactionIds = transactionDatabaseManager.getTransactionIds(blockId);

            for (final TransactionId transactionId : transactionIds) {
                final Sha256Hash transactionHash = transactionDatabaseManager.getTransactionHash(transactionId);

                final Boolean transactionExistsInUpdatedBlock = existingTransactionHashes.containsKey(transactionHash);

                if (transactionExistsInUpdatedBlock) {
                    final Transaction transaction = existingTransactionHashes.get(transactionHash);
                    Logger.log("Updating Transaction: " + transactionHash + " Id: " + transactionId);
                    transactionDatabaseManager.updateTransaction(transaction);
                    updatedTransactions.add(transactionHash);
                }
                else {
                    Logger.log("Deleting Transaction: " + transactionHash + " Id: " + transactionId);
                    transactionDatabaseManager.deleteTransaction(transactionId);
                }
            }
        }

        for (final Transaction transaction : block.getTransactions()) {
            final Sha256Hash transactionHash = transaction.getHash();
            final Boolean transactionHasBeenProcessed = updatedTransactions.contains(transactionHash);
            if (transactionHasBeenProcessed) { continue; }

            Logger.log("Inserting Transaction: " + transactionHash);
            final TransactionId transactionId = transactionDatabaseManager.insertTransaction(transaction);
            if (transactionId == null) { throw new DatabaseException("Error inserting Transaction."); }
        }
    }
}
