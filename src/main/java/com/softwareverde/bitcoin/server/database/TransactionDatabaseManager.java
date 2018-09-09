package com.softwareverde.bitcoin.server.database;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.server.database.cache.TransactionIdCache;
import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInputId;
import com.softwareverde.bitcoin.transaction.locktime.ImmutableLockTime;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.type.hash.sha256.MutableSha256Hash;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.BatchedInsertQuery;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.io.Logger;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.Util;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class TransactionDatabaseManager {
    public static final TransactionIdCache TRANSACTION_CACHE = new TransactionIdCache();

    protected final MysqlDatabaseConnection _databaseConnection;

    protected void _insertTransactionInputs(final BlockChainSegmentId blockChainSegmentId, final TransactionId transactionId, final Transaction transaction) throws DatabaseException {
        final TransactionInputDatabaseManager transactionInputDatabaseManager = new TransactionInputDatabaseManager(_databaseConnection);

        for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
            transactionInputDatabaseManager.insertTransactionInput(blockChainSegmentId, transactionId, transactionInput);
        }
    }

    protected void _insertTransactionOutputs(final TransactionId transactionId, final Transaction transaction) throws DatabaseException {
        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = new TransactionOutputDatabaseManager(_databaseConnection);

        for (final TransactionOutput transactionOutput : transaction.getTransactionOutputs()) {
            transactionOutputDatabaseManager.insertTransactionOutput(transactionId, transactionOutput);
        }
    }

    protected void _insertTransactionInputs(final BlockChainSegmentId blockChainSegmentId, final List<TransactionId> transactionIds, final List<Transaction> transactions) throws DatabaseException {
        final TransactionInputDatabaseManager transactionInputDatabaseManager = new TransactionInputDatabaseManager(_databaseConnection);

        final MutableList<List<TransactionInput>> transactionInputs = new MutableList<List<TransactionInput>>(transactions.getSize());
        for (final Transaction transaction : transactions) {
            transactionInputs.add(transaction.getTransactionInputs());
        }

        transactionInputDatabaseManager.insertTransactionInputs(blockChainSegmentId, transactionIds, transactionInputs);
    }

    protected void _insertTransactionOutputs(final List<TransactionId> transactionIds, final List<Transaction> transactions) throws DatabaseException {
        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = new TransactionOutputDatabaseManager(_databaseConnection);

        final MutableList<List<TransactionOutput>> transactionOutputs = new MutableList<List<TransactionOutput>>(transactions.getSize());
        for (final Transaction transaction : transactions) {
            transactionOutputs.add(transaction.getTransactionOutputs());
        }

        transactionOutputDatabaseManager.insertTransactionOutputs(transactionIds, transactionOutputs);
    }

    /**
     * Returns the transaction that matches the provided transactionHash, or null if one was not found.
     *  A matched transaction must belong to a block that is connected to the blockChainSegmentId.
     */
    protected TransactionId _getTransactionIdFromHash(final BlockChainSegmentId blockChainSegmentId, final Sha256Hash transactionHash) throws DatabaseException {
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(_databaseConnection);

        { // Attempt to find in cache first...
            final Map<BlockId, TransactionId> cachedTransactionIds = TRANSACTION_CACHE.getCachedTransactionIds(transactionHash);
            if (cachedTransactionIds != null) {
                for (final BlockId blockId : cachedTransactionIds.keySet()) {
                    final Boolean blockIsConnectedToChain = blockDatabaseManager.isBlockConnectedToChain(blockId, blockChainSegmentId);
                    if (blockIsConnectedToChain) {
                        return cachedTransactionIds.get(blockId);
                    }
                }
            }
        }

        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id, block_id FROM transactions WHERE hash = ? AND block_id IS NOT NULL")
                .setParameter(HexUtil.toHexString(transactionHash.getBytes()))
        );
        if (rows.isEmpty()) { return null; }

        TransactionId matchedTransactionId = null;
        for (final Row row : rows) {
            final TransactionId transactionId = TransactionId.wrap(row.getLong("id"));
            final BlockId blockId = BlockId.wrap(row.getLong("block_id"));

            TRANSACTION_CACHE.cacheTransactionId(blockId, transactionId, transactionHash); // Cache all of the found TransactionIds for this hash, even if they're not on this BlockChainSegment...

            if (matchedTransactionId == null) {
                final Boolean blockIsConnectedToChain = blockDatabaseManager.isBlockConnectedToChain(blockId, blockChainSegmentId);
                if (blockIsConnectedToChain) {
                    matchedTransactionId = TransactionId.wrap(row.getLong("id"));
                }
            }
        }

        return matchedTransactionId;
    }

    /**
     * Returns the transaction that matches the provided transactionHash, or null if one was not found.
     *  A matched transaction must belong to a block that has not been assigned a blockChainSegmentId.
     */
    protected TransactionId _getUncommittedTransactionIdFromHash(final Sha256Hash transactionHash) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT transactions.id, transactions.block_id FROM transactions INNER JOIN blocks ON blocks.id = transactions.block_id WHERE blocks.block_chain_segment_id IS NULL AND transactions.hash = ?")
                .setParameter(HexUtil.toHexString(transactionHash.getBytes()))
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return TransactionId.wrap(row.getLong("id"));
    }

    protected TransactionId _getTransactionIdFromHash(final BlockId blockId, final Sha256Hash transactionHash) throws DatabaseException {
        final TransactionId cachedTransactionId = TRANSACTION_CACHE.getCachedTransactionId(blockId, transactionHash);
        if (cachedTransactionId != null) { return cachedTransactionId; }

        final java.util.List<Row> rows = _databaseConnection.query(
            new Query(
                "SELECT id FROM transactions WHERE block_id = ? AND hash = ?")
                .setParameter(blockId)
                .setParameter(HexUtil.toHexString(transactionHash.getBytes()))
        );

        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final TransactionId transactionId = TransactionId.wrap(row.getLong("id"));

        TRANSACTION_CACHE.cacheTransactionId(blockId, transactionId, transactionHash);

        return transactionId;
    }

    protected void _updateTransaction(final TransactionId transactionId, final BlockId blockId, final Transaction transaction) throws DatabaseException {
        final LockTime lockTime = transaction.getLockTime();
        _databaseConnection.executeSql(
            new Query("UPDATE transactions SET hash = ?, block_id = ?, version = ? = ?, lock_time = ? WHERE id = ?")
                .setParameter(HexUtil.toHexString(transaction.getHash().getBytes()))
                .setParameter(blockId)
                .setParameter(transaction.getVersion())
                .setParameter(lockTime.getValue())
                .setParameter(transactionId)
        );

        TRANSACTION_CACHE.cacheTransactionId(blockId, transactionId, transaction.getHash());
    }

    protected TransactionId _insertTransaction(final BlockId blockId, final Transaction transaction) throws DatabaseException {
        final LockTime lockTime = transaction.getLockTime();
        final TransactionId transactionId = TransactionId.wrap(_databaseConnection.executeSql(
            new Query("INSERT INTO transactions (hash, block_id, version, lock_time) VALUES (?, ?, ?, ?)")
                .setParameter(transaction.getHash())
                .setParameter(blockId)
                .setParameter(transaction.getVersion())
                .setParameter(lockTime.getValue())
        ));

        final Boolean shouldCacheTransaction = (blockId != null);
        if (shouldCacheTransaction) {
            TRANSACTION_CACHE.cacheTransactionId(blockId, transactionId, transaction.getHash());
        }

        return transactionId;
    }

    protected List<TransactionId> _insertTransactions(final BlockId blockId, final List<Transaction> transactions) throws DatabaseException {
        final Query batchedInsertQuery = new BatchedInsertQuery("INSERT INTO transactions (hash, block_id, version, lock_time) VALUES (?, ?, ?, ?)");
        for (final Transaction transaction : transactions) {
            final LockTime lockTime = transaction.getLockTime();

            batchedInsertQuery.setParameter(transaction.getHash());
            batchedInsertQuery.setParameter(blockId);
            batchedInsertQuery.setParameter(transaction.getVersion());
            batchedInsertQuery.setParameter(lockTime.getValue());
        }

        final Long firstTransactionId = _databaseConnection.executeSql(batchedInsertQuery);
        if (firstTransactionId == null) { return null; }

        final MutableList<TransactionId> transactionIds = new MutableList<TransactionId>(transactions.getSize());
        for (int i = 0; i < transactions.getSize(); ++i) {
            final Transaction transaction = transactions.get(i);
            final TransactionId transactionId = TransactionId.wrap(firstTransactionId + i);

            transactionIds.add(transactionId);
            TRANSACTION_CACHE.cacheTransactionId(blockId, transactionId, transaction.getHash());
        }
        return transactionIds;
    }

    protected MutableTransaction _inflateTransaction(final TransactionId transactionId) throws DatabaseException {
        final TransactionInputDatabaseManager transactionInputDatabaseManager = new TransactionInputDatabaseManager(_databaseConnection);
        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = new TransactionOutputDatabaseManager(_databaseConnection);

        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT * FROM transactions WHERE id = ?")
                .setParameter(transactionId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final Long version = row.getLong("version");
        final LockTime lockTime = new ImmutableLockTime(row.getLong("lock_time"));

        final MutableTransaction transaction = new MutableTransaction();

        transaction.setVersion(version);
        transaction.setLockTime(lockTime);

        // TODO: Move query to TransactionInputDatabaseManager...
        final java.util.List<Row> transactionInputRows = _databaseConnection.query(
            new Query("SELECT id FROM transaction_inputs WHERE transaction_id = ? ORDER BY id ASC")
                .setParameter(transactionId)
        );
        for (final Row transactionInputRow : transactionInputRows) {
            final TransactionInputId transactionInputId = TransactionInputId.wrap(transactionInputRow.getLong("id"));
            final TransactionInput transactionInput = transactionInputDatabaseManager.getTransactionInput(transactionInputId);
            transaction.addTransactionInput(transactionInput);
        }

        // TODO: Move query to TransactionOutputDatabaseManager...
        final java.util.List<Row> transactionOutputRows = _databaseConnection.query(
            new Query("SELECT id FROM transaction_outputs WHERE transaction_id = ? ORDER BY id ASC")
                .setParameter(transactionId)
        );
        for (final Row transactionOutputRow : transactionOutputRows) {
            final TransactionOutputId transactionOutputId = TransactionOutputId.wrap(transactionOutputRow.getLong("id"));
            final TransactionOutput transactionOutput = transactionOutputDatabaseManager.getTransactionOutput(transactionOutputId);
            transaction.addTransactionOutput(transactionOutput);
        }

        { // Validate inflated transaction hash...
            final Sha256Hash expectedTransactionHash = MutableSha256Hash.fromHexString(row.getString("hash"));
            if (! Util.areEqual(expectedTransactionHash, transaction.getHash())) {
                Logger.log("ERROR: Error inflating transaction: " + expectedTransactionHash);
                Logger.log(transaction.toJson());
                return null;
            }
        }

        return transaction;
    }

    public TransactionDatabaseManager(final MysqlDatabaseConnection databaseConnection) {
        _databaseConnection = databaseConnection;
    }

    public TransactionId insertTransaction(final BlockChainSegmentId blockChainSegmentId, final BlockId blockId, final Transaction transaction) throws DatabaseException {
        final TransactionId transactionId = _insertTransaction(blockId, transaction);

        _insertTransactionOutputs(transactionId, transaction);
        _insertTransactionInputs(blockChainSegmentId, transactionId, transaction);

        return transactionId;
    }

    public void insertTransactions(final BlockChainSegmentId blockChainSegmentId, final BlockId blockId, final List<Transaction> transactions) throws DatabaseException {
        final List<TransactionId> transactionIds = _insertTransactions(blockId, transactions);

        _insertTransactionOutputs(transactionIds, transactions); // NOTE: Since this is a bulk-insert and the following inputs may reference these outputs, insert the TransactionOutputs first...
        _insertTransactionInputs(blockChainSegmentId, transactionIds, transactions);

        // TODO: Bulk-inserting does not validate the order of the transactions (inputs may be (incorrectly) spending outputs that are in this batch, but not in the correct order).  Consider asserting that the order of the transactions is correct.
    }

    /**
     * Returns all transactionIds matching the hash.
     *  This search includes uncommitted transactions, transactions in the mempool, and confirmed transactions.
     */
    public List<TransactionId> getTransactionIdsFromHash(final Sha256Hash transactionHash) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM transactions WHERE hash = ?")
                .setParameter(transactionHash)
        );

        final MutableList<TransactionId> transactionIds = new MutableList<TransactionId>(rows.size());
        for (final Row row : rows) {
            final TransactionId transactionId = TransactionId.wrap(row.getLong("id"));
            transactionIds.add(transactionId);
        }

        return transactionIds;
    }

    /**
     * Attempts to find the transaction that matches transactionHash that was published on the provided blockChainSegmentId.
     *  This function is intended to be used when the blockId is not known.
     *  Uncommitted transactions are NOT included in this search.
     */
    public TransactionId getTransactionIdFromHash(final BlockChainSegmentId blockChainSegmentId, final Sha256Hash transactionHash) throws DatabaseException {
        final TransactionId transactionId = _getTransactionIdFromHash(blockChainSegmentId, transactionHash);
        return transactionId;
    }

    public Sha256Hash getTransactionHash(final TransactionId transactionId) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id, hash FROM transactions WHERE id = ?")
                .setParameter(transactionId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return MutableSha256Hash.fromHexString(row.getString("hash"));
    }

    /**
     * Attempts to find the transaction that matches transactionHash whose block has not been committed to a BlockChainSegment.
     *  This function is intended to be used when the blockId is not known.
     *  Only uncommitted transactions (i.e. transactions have been assigned to the block that is currently being stored...) are included in the search.
     */
    public TransactionId getUncommittedTransactionIdFromHash(final Sha256Hash transactionHash) throws DatabaseException {
        // TODO: Attempt to remove this function, and have methods using this use TransactionDatabaseManager:getTransactionIdFromHash(BlockId, Sha256Hash) instead...
        return _getUncommittedTransactionIdFromHash(transactionHash);
    }

    public TransactionId getTransactionIdFromMemoryPool(final Sha256Hash transactionHash) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM transactions WHERE hash = ? AND block_id IS NULL")
                .setParameter(transactionHash)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return TransactionId.wrap(row.getLong("id"));
    }

    public TransactionId insertTransactionIntoMemoryPool(final Transaction transaction) throws DatabaseException {
        final TransactionId transactionId = _insertTransaction(null, transaction);
        _insertTransactionOutputs(transactionId, transaction);

        final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(_databaseConnection);
        final BlockChainSegmentId blockChainSegmentId = blockChainDatabaseManager.getHeadBlockChainSegmentId();

        _insertTransactionInputs(blockChainSegmentId, transactionId, transaction);
        return transactionId;
    }

    public TransactionId getTransactionIdFromHash(final BlockId blockId, final Sha256Hash transactionHash) throws DatabaseException {
        return _getTransactionIdFromHash(blockId, transactionHash);
    }

    public MutableTransaction getTransaction(final TransactionId transactionId) throws DatabaseException {
        return _inflateTransaction(transactionId);
    }

    public List<TransactionId> getTransactionIds(final BlockId blockId) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM transactions WHERE block_id = ? ORDER BY id ASC")
                .setParameter(blockId)
        );

        final ImmutableListBuilder<TransactionId> listBuilder = new ImmutableListBuilder<TransactionId>(rows.size());
        for (final Row row : rows) {
            final TransactionId transactionId = TransactionId.wrap(row.getLong("id"));
            listBuilder.add(transactionId);
        }
        return listBuilder.build();
    }

    public List<Transaction> getTransaction(final BlockId blockId) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM transactions WHERE block_id = ? ORDER BY id ASC")
                .setParameter(blockId)
        );

        final ImmutableListBuilder<Transaction> listBuilder = new ImmutableListBuilder<Transaction>(rows.size());
        for (final Row row : rows) {
            final TransactionId transactionId = TransactionId.wrap(row.getLong("id"));
            final Transaction transaction = _inflateTransaction(transactionId);
            listBuilder.add(transaction);
        }
        return listBuilder.build();
    }

    public BlockId getBlockId(final TransactionId transactionId) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id, block_id FROM transactions WHERE id = ?")
                .setParameter(transactionId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return BlockId.wrap(row.getLong("block_id"));
    }

    public Integer getTransactionCount(final BlockId blockId) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT block_id, COUNT(*) AS transaction_count FROM transactions WHERE block_id = ?")
                .setParameter(blockId)
        );

        if (rows.isEmpty()) { return null; }
        final Row row = rows.get(0);

        return row.getInteger("transaction_count");
    }

    public void updateTransaction(final TransactionId transactionId, final BlockId blockId, final Transaction transaction) throws DatabaseException {
        _updateTransaction(transactionId, blockId, transaction);

        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(_databaseConnection);
        final BlockChainSegmentId blockChainSegmentId = blockDatabaseManager.getBlockChainSegmentId(blockId);

        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = new TransactionOutputDatabaseManager(_databaseConnection);
        final TransactionInputDatabaseManager transactionInputDatabaseManager = new TransactionInputDatabaseManager(_databaseConnection);

        { // Process TransactionOutputs....
            final List<TransactionOutputId> transactionOutputIds = transactionOutputDatabaseManager.getTransactionOutputIds(transactionId);
            final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();

            final HashMap<Integer, TransactionOutput> transactionOutputMap = new HashMap<Integer, TransactionOutput>();
            {
                for (final TransactionOutput transactionOutput : transactionOutputs) {
                    transactionOutputMap.put(transactionOutput.getIndex(), transactionOutput);
                }
            }

            final Set<Integer> processedTransactionOutputIndexes = new TreeSet<Integer>();
            for (final TransactionOutputId transactionOutputId : transactionOutputIds) {
                final TransactionOutput transactionOutput = transactionOutputDatabaseManager.getTransactionOutput(transactionOutputId);

                final Integer transactionOutputIndex = transactionOutput.getIndex();
                final Boolean transactionOutputExistsInUpdatedTransaction = transactionOutputMap.containsKey(transactionOutputIndex);
                if (transactionOutputExistsInUpdatedTransaction) {
                    transactionOutputDatabaseManager.updateTransactionOutput(transactionOutputId, transactionId, transactionOutput);
                    processedTransactionOutputIndexes.add(transactionOutputIndex);
                }
                else {
                    transactionOutputDatabaseManager.deleteTransactionOutput(transactionOutputId);
                }
            }

            for (final TransactionOutput transactionOutput : transactionOutputs) {
                final Integer transactionOutputIndex = transactionOutput.getIndex();
                final Boolean transactionOutputHasBeenProcessed = processedTransactionOutputIndexes.contains(transactionOutputIndex);
                if (!transactionOutputHasBeenProcessed) {
                    transactionOutputDatabaseManager.insertTransactionOutput(transactionId, transactionOutput);
                }
            }
        }

        { // Process TransactionInputs....
            final List<TransactionInputId> transactionInputIds = transactionInputDatabaseManager.getTransactionInputIds(transactionId);
            final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();

            final HashMap<TransactionOutputIdentifier, TransactionInput> transactionInputMap = new HashMap<TransactionOutputIdentifier, TransactionInput>();
            {
                for (final TransactionInput transactionInput : transactionInputs) {
                    final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(blockChainSegmentId, transactionInput.getPreviousOutputTransactionHash(), transactionInput.getPreviousOutputIndex());
                    transactionInputMap.put(transactionOutputIdentifier, transactionInput);
                }
            }

            final Set<TransactionOutputIdentifier> processedTransactionInputIndexes = new TreeSet<TransactionOutputIdentifier>();
            for (final TransactionInputId transactionInputId : transactionInputIds) {
                final TransactionInput transactionInput = transactionInputDatabaseManager.getTransactionInput(transactionInputId);

                final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(blockChainSegmentId, transactionInput.getPreviousOutputTransactionHash(), transactionInput.getPreviousOutputIndex());
                final Boolean transactionInputExistsInUpdatedTransaction = transactionInputMap.containsKey(transactionOutputIdentifier);
                if (transactionInputExistsInUpdatedTransaction) {
                    transactionInputDatabaseManager.updateTransactionInput(transactionInputId, blockChainSegmentId, transactionId, transactionInput);
                    processedTransactionInputIndexes.add(transactionOutputIdentifier);
                }
                else {
                    transactionInputDatabaseManager.deleteTransactionInput(transactionInputId);
                }
            }

            for (final TransactionInput transactionInput : transactionInputs) {
                final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(blockChainSegmentId, transactionInput.getPreviousOutputTransactionHash(), transactionInput.getPreviousOutputIndex());
                final Boolean transactionInputHasBeenProcessed = processedTransactionInputIndexes.contains(transactionOutputIdentifier);
                if (! transactionInputHasBeenProcessed) {
                    transactionInputDatabaseManager.insertTransactionInput(blockChainSegmentId, transactionId, transactionInput);
                }
            }
        }
    }

    public void deleteTransaction(final TransactionId transactionId) throws DatabaseException {
        final TransactionInputDatabaseManager transactionInputDatabaseManager = new TransactionInputDatabaseManager(_databaseConnection);
        final List<TransactionInputId> transactionInputIds = transactionInputDatabaseManager.getTransactionInputIds(transactionId);
        for (final TransactionInputId transactionInputId : transactionInputIds) {
            transactionInputDatabaseManager.deleteTransactionInput(transactionInputId);
        }

        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = new TransactionOutputDatabaseManager(_databaseConnection);
        final List<TransactionOutputId> transactionOutputIds = transactionOutputDatabaseManager.getTransactionOutputIds(transactionId);
        for (final TransactionOutputId transactionOutputId : transactionOutputIds) {
            transactionOutputDatabaseManager.deleteTransactionOutput(transactionOutputId);
        }

        _databaseConnection.executeSql(
            new Query("DELETE FROM transactions WHERE id = ?")
                .setParameter(transactionId)
        );
    }
}
