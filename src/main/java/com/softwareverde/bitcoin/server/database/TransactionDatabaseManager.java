package com.softwareverde.bitcoin.server.database;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
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
import com.softwareverde.util.Util;

import java.util.HashMap;
import java.util.Set;
import java.util.TreeSet;

public class TransactionDatabaseManager {
    public static final Object BLOCK_TRANSACTIONS_WRITE_MUTEX = new Object();

    protected final MysqlDatabaseConnection _databaseConnection;

    protected void _insertTransactionInputs(final TransactionId transactionId, final Transaction transaction) throws DatabaseException {
        final TransactionInputDatabaseManager transactionInputDatabaseManager = new TransactionInputDatabaseManager(_databaseConnection);

        for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
            transactionInputDatabaseManager.insertTransactionInput(transactionId, transactionInput);
        }
    }

    protected void _insertTransactionOutputs(final TransactionId transactionId, final Transaction transaction) throws DatabaseException {
        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = new TransactionOutputDatabaseManager(_databaseConnection);

        for (final TransactionOutput transactionOutput : transaction.getTransactionOutputs()) {
            transactionOutputDatabaseManager.insertTransactionOutput(transactionId, transactionOutput);
        }
    }

    /**
     * Returns the transaction that matches the provided transactionHash, or null if one was not found.
     */
    protected TransactionId _getTransactionIdFromHash(final Sha256Hash transactionHash) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM transactions WHERE hash = ?")
                .setParameter(transactionHash)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return TransactionId.wrap(row.getLong("id"));
    }

    protected void _updateTransaction(final TransactionId transactionId, final Transaction transaction) throws DatabaseException {
        final LockTime lockTime = transaction.getLockTime();
        _databaseConnection.executeSql(
            new Query("UPDATE transactions SET hash = ?, version = ?, lock_time = ? WHERE id = ?")
                .setParameter(transaction.getHash())
                .setParameter(transaction.getVersion())
                .setParameter(lockTime.getValue())
                .setParameter(transactionId)
        );
    }

    protected Integer _getTransactionCount(final BlockId blockId) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT COUNT(*) AS transaction_count FROM block_transactions WHERE block_id = ?")
                .setParameter(blockId)
        );

        if (rows.isEmpty()) { return null; }
        final Row row = rows.get(0);

        return row.getInteger("transaction_count");
    }

    protected List<BlockId> _getBlockIds(final TransactionId transactionId) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id, block_id FROM block_transactions WHERE transaction_id = ?")
                .setParameter(transactionId)
        );
        if (rows.isEmpty()) { return null; }

        final MutableList<BlockId> blockIds = new MutableList<BlockId>(rows.size());
        for (final Row row : rows) {
            final Long blockId = row.getLong("block_id");
            blockIds.add(BlockId.wrap(blockId));
        }
        return blockIds;
    }

    protected TransactionId _insertTransaction(final Transaction transaction) throws DatabaseException {
        final LockTime lockTime = transaction.getLockTime();
        final Long transactionId = _databaseConnection.executeSql(
            new Query("INSERT INTO transactions (hash, version, lock_time) VALUES (?, ?, ?)")
                .setParameter(transaction.getHash())
                .setParameter(transaction.getVersion())
                .setParameter(lockTime.getValue())
        );

        return TransactionId.wrap(transactionId);
    }

    protected List<TransactionId> _insertTransactions(final List<Transaction> transactions) throws DatabaseException {
        final Query batchedInsertQuery = new BatchedInsertQuery("INSERT INTO transactions (hash, version, lock_time) VALUES (?, ?, ?)");
        for (final Transaction transaction : transactions) {
            final LockTime lockTime = transaction.getLockTime();

            batchedInsertQuery.setParameter(transaction.getHash());
            batchedInsertQuery.setParameter(transaction.getVersion());
            batchedInsertQuery.setParameter(lockTime.getValue());
        }

        final Long firstTransactionId = _databaseConnection.executeSql(batchedInsertQuery);
        if (firstTransactionId == null) { return null; }

        final MutableList<TransactionId> transactionIds = new MutableList<TransactionId>(transactions.getSize());
        for (int i = 0; i < transactions.getSize(); ++i) {
            final TransactionId transactionId = TransactionId.wrap(firstTransactionId + i);
            transactionIds.add(transactionId);
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
            final Sha256Hash expectedTransactionHash = Sha256Hash.fromHexString(row.getString("hash"));
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

    public TransactionId insertTransaction(final Transaction transaction) throws DatabaseException {
        final Sha256Hash transactionHash = transaction.getHash();
        final TransactionId existingTransactionId = _getTransactionIdFromHash(transactionHash);
        if (existingTransactionId != null) {
            return existingTransactionId;
        }

        final TransactionId transactionId = _insertTransaction(transaction);
        _insertTransactionOutputs(transactionId, transaction);
        _insertTransactionInputs(transactionId, transaction);

        return transactionId;
    }

    public void associateTransactionToBlock(final TransactionId transactionId, final BlockId blockId) throws DatabaseException {
        synchronized (BLOCK_TRANSACTIONS_WRITE_MUTEX) {
            final Integer currentTransactionCount = _getTransactionCount(blockId);
            _databaseConnection.executeSql(
                new Query("INSERT INTO block_transactions (block_id, transaction_id, sort_order) VALUES (?, ?, ?)")
                    .setParameter(blockId)
                    .setParameter(transactionId)
                    .setParameter(currentTransactionCount)
            );
        }
    }

    public TransactionId getTransactionIdFromHash(final Sha256Hash transactionHash) throws DatabaseException {
        return _getTransactionIdFromHash(transactionHash);
    }

    public Sha256Hash getTransactionHash(final TransactionId transactionId) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id, hash FROM transactions WHERE id = ?")
                .setParameter(transactionId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return Sha256Hash.fromHexString(row.getString("hash"));
    }

    public MutableTransaction getTransaction(final TransactionId transactionId) throws DatabaseException {
        return _inflateTransaction(transactionId);
    }

    public List<TransactionId> getTransactionIds(final BlockId blockId) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id, transaction_id FROM block_transactions WHERE block_id = ? ORDER BY sort_order ASC")
                .setParameter(blockId)
        );

        final ImmutableListBuilder<TransactionId> listBuilder = new ImmutableListBuilder<TransactionId>(rows.size());
        for (final Row row : rows) {
            final TransactionId transactionId = TransactionId.wrap(row.getLong("transaction_id"));
            listBuilder.add(transactionId);
        }
        return listBuilder.build();
    }

    public Integer getTransactionCount(final BlockId blockId) throws DatabaseException {
        return _getTransactionCount(blockId);
    }

    public BlockId getBlockId(final BlockChainSegmentId blockChainSegmentId, final TransactionId transactionId) throws DatabaseException {
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(_databaseConnection);

        final List<BlockId> blockIds = _getBlockIds(transactionId);
        for (final BlockId blockId : blockIds) {
            final Boolean isConnected = blockDatabaseManager.isBlockConnectedToChain(blockId, blockChainSegmentId, BlockRelationship.ANY);
            if (isConnected) {
                return blockId;
            }
        }

        return null;
    }

    public List<BlockId> getBlockIds(final TransactionId transactionId) throws DatabaseException {
        return _getBlockIds(transactionId);
    }

    public List<BlockId> getBlockIds(final Sha256Hash transactionHash) throws DatabaseException {
        final TransactionId transactionId = _getTransactionIdFromHash(transactionHash);
        if (transactionId == null) { return new MutableList<BlockId>(); }

        return _getBlockIds(transactionId);
    }

    public void updateTransaction(final Transaction transaction) throws DatabaseException {
        final TransactionId transactionId = _getTransactionIdFromHash(transaction.getHash());

        _updateTransaction(transactionId, transaction);

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
                    final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionInput.getPreviousOutputTransactionHash(), transactionInput.getPreviousOutputIndex());
                    transactionInputMap.put(transactionOutputIdentifier, transactionInput);
                }
            }

            final Set<TransactionOutputIdentifier> processedTransactionInputIndexes = new TreeSet<TransactionOutputIdentifier>();
            for (final TransactionInputId transactionInputId : transactionInputIds) {
                final TransactionInput transactionInput = transactionInputDatabaseManager.getTransactionInput(transactionInputId);

                final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionInput.getPreviousOutputTransactionHash(), transactionInput.getPreviousOutputIndex());
                final Boolean transactionInputExistsInUpdatedTransaction = transactionInputMap.containsKey(transactionOutputIdentifier);
                if (transactionInputExistsInUpdatedTransaction) {
                    transactionInputDatabaseManager.updateTransactionInput(transactionInputId, transactionId, transactionInput);
                    processedTransactionInputIndexes.add(transactionOutputIdentifier);
                }
                else {
                    transactionInputDatabaseManager.deleteTransactionInput(transactionInputId);
                }
            }

            for (final TransactionInput transactionInput : transactionInputs) {
                final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionInput.getPreviousOutputTransactionHash(), transactionInput.getPreviousOutputIndex());
                final Boolean transactionInputHasBeenProcessed = processedTransactionInputIndexes.contains(transactionOutputIdentifier);
                if (! transactionInputHasBeenProcessed) {
                    transactionInputDatabaseManager.insertTransactionInput(transactionId, transactionInput);
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
