package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.context.UnspentTransactionOutputContext;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.BatchedInsertQuery;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class UndoLogCreator {
    public static final ReentrantReadWriteLock.WriteLock WRITE_LOCK;
    public static final ReentrantReadWriteLock.ReadLock READ_LOCK;
    static {
        final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        WRITE_LOCK = readWriteLock.writeLock();
        READ_LOCK = readWriteLock.readLock();
    }

    public static final Integer MAX_REORG_DEPTH = 256;

    public void createUndoLog(final Long blockHeight, final Block block, final UnspentTransactionOutputContext unspentTransactionOutputContext, final DatabaseConnection databaseConnection) throws DatabaseException {
        if (! UndoLogCreator.WRITE_LOCK.isHeldByCurrentThread()) {
            throw new DatabaseException("Attempted to create UndoLog without acquiring mutex.");
        }

        final List<Transaction> transactions = block.getTransactions();
        final int transactionCount = transactions.getCount();

        final HashMap<TransactionOutputIdentifier, TransactionOutput> transactionOutputs = new HashMap<>();
        final MutableList<TransactionOutputIdentifier> missingPreviousOutputs = new MutableList<>();
        final HashMap<Sha256Hash, Transaction> blockTransactions = new HashMap<>(transactionCount);

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

                transactionOutputs.put(transactionOutputIdentifier, unspentTransactionOutput);
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
        }

        if (transactionOutputs.isEmpty()) { return; } // Block contains only its coinbase...

        final Long expiresAfterBlockHeight = (blockHeight + UndoLogCreator.MAX_REORG_DEPTH);

        final BatchedInsertQuery query = new BatchedInsertQuery("INSERT INTO pruned_previous_transaction_outputs (transaction_hash, `index`, expires_after_block_height, amount, locking_script) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE expires_after_block_height = GREATEST(VALUES(expires_after_block_height), expires_after_block_height);");
        for (Map.Entry<TransactionOutputIdentifier, TransactionOutput> transactionOutputEntry : transactionOutputs.entrySet()) {
            final TransactionOutputIdentifier transactionOutputIdentifier = transactionOutputEntry.getKey();
            final TransactionOutput transactionOutput = transactionOutputEntry.getValue();

            final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
            final Integer outputIndex = transactionOutputIdentifier.getOutputIndex();

            final Long amount = transactionOutput.getAmount();
            final LockingScript lockingScript = transactionOutput.getLockingScript();

            query.setParameter(transactionHash);
            query.setParameter(outputIndex);
            query.setParameter(expiresAfterBlockHeight);
            query.setParameter(amount);
            query.setParameter(lockingScript.getBytes());
        }

        databaseConnection.executeSql(query);
    }
}
