package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.context.UnspentTransactionOutputContext;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.BatchedInsertQuery;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.timer.MilliTimer;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class UndoLogDatabaseManager {
    public static final ReentrantReadWriteLock.WriteLock WRITE_LOCK;
    public static final ReentrantReadWriteLock.ReadLock READ_LOCK;
    static {
        final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        WRITE_LOCK = readWriteLock.writeLock();
        READ_LOCK = readWriteLock.readLock();
    }

    public static final Integer MAX_REORG_DEPTH = 288;

    protected final DatabaseManager _databaseManager;

    protected void _createUndoLog(final Long blockHeight, final Block block, final UnspentTransactionOutputContext unspentTransactionOutputContext, final DatabaseConnection databaseConnection) throws DatabaseException {
        final List<Transaction> transactions = block.getTransactions();
        final int transactionCount = transactions.getCount();

        final HashMap<TransactionOutputIdentifier, TransactionOutput> transactionOutputs = new HashMap<>();
        final MutableList<TransactionOutputIdentifier> missingPreviousOutputs = new MutableList<>();
        final HashMap<Sha256Hash, Transaction> blockTransactions = new HashMap<>(transactionCount);
        final HashMap<TransactionOutputIdentifier, Long> utxoBlockHeights = new HashMap<>();

        boolean isCoinbase = true;
        for (final Transaction transaction : transactions) {
            final Sha256Hash transactionHash = transaction.getHash();
            blockTransactions.put(transactionHash, transaction);

            if (isCoinbase) {
                isCoinbase = false;
                continue;
            }

            for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
                final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
                final TransactionOutput unspentTransactionOutput = unspentTransactionOutputContext.getTransactionOutput(transactionOutputIdentifier);
                if (unspentTransactionOutput == null) {
                    missingPreviousOutputs.add(transactionOutputIdentifier);
                    continue;
                }

                final Long utxoBlockHeight = unspentTransactionOutputContext.getBlockHeight(transactionOutputIdentifier);

                transactionOutputs.put(transactionOutputIdentifier, unspentTransactionOutput);
                utxoBlockHeights.put(transactionOutputIdentifier, utxoBlockHeight);
            }
        }

        for (final TransactionOutputIdentifier transactionOutputIdentifier : missingPreviousOutputs) {
            final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
            final Transaction transaction = blockTransactions.get(transactionHash);
            if (transaction == null) {
                throw new DatabaseException("Unable to load output for undo log: " + transactionOutputIdentifier);
            }

            final Integer outputIndex = transactionOutputIdentifier.getOutputIndex();
            final List<TransactionOutput> outputs = transaction.getTransactionOutputs();

            if (outputIndex >= outputs.getCount()) {
                throw new DatabaseException("Unable to load output for undo log: " + transactionOutputIdentifier);
            }

            final TransactionOutput transactionOutput = outputs.get(outputIndex);
            transactionOutputs.put(transactionOutputIdentifier, transactionOutput);
            utxoBlockHeights.put(transactionOutputIdentifier, blockHeight);
        }

        if (transactionOutputs.isEmpty()) { return; } // Block contains only its coinbase...

        final Long expiresAfterBlockHeight = (blockHeight + UndoLogDatabaseManager.MAX_REORG_DEPTH);

        final BatchedInsertQuery query = new BatchedInsertQuery("INSERT INTO pruned_previous_transaction_outputs (transaction_hash, `index`, block_height, amount, locking_script, expires_after_block_height) VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE block_height = VALUES(block_height), expires_after_block_height = GREATEST(VALUES(expires_after_block_height), expires_after_block_height)");
        for (Map.Entry<TransactionOutputIdentifier, TransactionOutput> transactionOutputEntry : transactionOutputs.entrySet()) {
            final TransactionOutputIdentifier transactionOutputIdentifier = transactionOutputEntry.getKey();
            final TransactionOutput transactionOutput = transactionOutputEntry.getValue();
            final Long utxoBlockHeight = utxoBlockHeights.get(transactionOutputIdentifier);

            final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
            final Integer outputIndex = transactionOutputIdentifier.getOutputIndex();

            final Long amount = transactionOutput.getAmount();
            final LockingScript lockingScript = transactionOutput.getLockingScript();
            final ByteArray lockingScriptBytes = lockingScript.getBytes();

            query.setParameter(transactionHash);
            query.setParameter(outputIndex);
            query.setParameter(utxoBlockHeight);
            query.setParameter(amount);
            query.setParameter(lockingScriptBytes);
            query.setParameter(expiresAfterBlockHeight);
        }

        databaseConnection.executeSql(query);
    }

    protected void _clearExpiredPrunedOutputs(final Long committedBlockHeight, final DatabaseConnection databaseConnection) throws DatabaseException {
        // Delete expired pruning/undoLog outputs...
        final MilliTimer milliTimer = new MilliTimer();
        milliTimer.start();

        // final java.util.List<Row> heightRows = databaseConnection.query(
        //     new Query("SELECT DISTINCT expires_after_block_height FROM pruned_previous_transaction_outputs WHERE expires_after_block_height >= ? ORDER BY expires_after_block_height ASC")
        //         .setParameter(committedBlockHeight)
        // );
        // for (final Row row : heightRows) {
        //     final Long blockHeight = row.getLong("expires_after_block_height");
        //
        //     databaseConnection.executeSql(
        //         new Query("INSERT INTO pruned_previous_transaction_outputs_buffer SELECT * FROM pruned_previous_transaction_outputs WHERE expires_after_block_height = ?")
        //             .setParameter(blockHeight)
        //     );
        // }
        databaseConnection.executeSql(
            new Query("INSERT INTO pruned_previous_transaction_outputs_buffer SELECT * FROM pruned_previous_transaction_outputs WHERE expires_after_block_height >= ?")
                .setParameter(committedBlockHeight)
        );

        // NOTICE: The method of table rotation and truncation causes an implicit database commit.

        // databaseConnection.executeDdl(
        //     new Query("RENAME TABLE pruned_previous_transaction_outputs TO pruned_previous_transaction_outputs2, pruned_previous_transaction_outputs_buffer TO pruned_previous_transaction_outputs, pruned_previous_transaction_outputs2 TO pruned_previous_transaction_outputs_buffer")
        //         .setParameter(newCommittedBlockHeight)
        // );
        //
        // databaseConnection.executeDdl(
        //     new Query("TRUNCATE TABLE pruned_previous_transaction_outputs_buffer")
        //         .setParameter(newCommittedBlockHeight)
        // );
        databaseConnection.executeSql(
            new Query("CALL ROTATE_PRUNED_PREVIOUS_TRANSACTION_OUTPUTS")
        );

        milliTimer.stop();
        Logger.trace("Cleared expired pruned outputs in " + milliTimer.getMillisecondsElapsed() + "ms.");
    }

    public UndoLogDatabaseManager(final DatabaseManager databaseManager) {
        _databaseManager = databaseManager;
    }

    public void createUndoLog(final Long blockHeight, final Block block, final UnspentTransactionOutputContext unspentTransactionOutputContext) throws DatabaseException {
        try {
            WRITE_LOCK.lock();

            final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
            _createUndoLog(blockHeight, block, unspentTransactionOutputContext, databaseConnection);
        }
        finally {
            WRITE_LOCK.unlock();
        }
    }

    public void clearExpiredPrunedOutputs(final Long committedBlockHeight) throws DatabaseException {
        try {
            WRITE_LOCK.lock();

            final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
            _clearExpiredPrunedOutputs(committedBlockHeight, databaseConnection);
        }
        finally {
            WRITE_LOCK.unlock();
        }
    }
}
