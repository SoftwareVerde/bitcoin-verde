package com.softwareverde.bitcoin.server.module.node.handler.transaction;

import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.output.UnconfirmedTransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.UnconfirmedTransactionOutputId;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.security.hash.sha256.Sha256Hash;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

public class OrphanedTransactionsCache {
    public static final Integer MAX_ORPHANED_TRANSACTION_COUNT = (128 * 1024);

    protected final HashMap<TransactionOutputIdentifier, HashSet<Transaction>> _orphanedTransactions = new HashMap<TransactionOutputIdentifier, HashSet<Transaction>>(MAX_ORPHANED_TRANSACTION_COUNT);
    protected final HashSet<Transaction> _orphanedTransactionSet = new HashSet<Transaction>();
    protected final LinkedList<Transaction> _orphanedTransactionsByAge = new LinkedList<Transaction>();

    protected void _removeOrphanedTransaction(final Transaction transaction) {
        _orphanedTransactionSet.remove(transaction);

        for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
            final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
            final HashSet<Transaction> queuedTransactions = _orphanedTransactions.get(transactionOutputIdentifier);
            if (queuedTransactions == null) { continue; }

            queuedTransactions.remove(transaction);

            if (queuedTransactions.isEmpty()) {
                _orphanedTransactions.remove(transactionOutputIdentifier);
            }
        }

        Logger.debug("Purging old orphaned Transaction: " + transaction.getHash() + " (" + _orphanedTransactionSet.size() + " / " + MAX_ORPHANED_TRANSACTION_COUNT + ")");
    }

    protected void _purgeOldTransactions() {
        final int itemsToRemoveCount = (MAX_ORPHANED_TRANSACTION_COUNT / 2);
        for (int i = 0; i < itemsToRemoveCount; ++i) {
            if (_orphanedTransactionsByAge.isEmpty()) { break; }

            final Transaction transaction = _orphanedTransactionsByAge.removeFirst();
            if (_orphanedTransactionSet.contains(transaction)) {
                _removeOrphanedTransaction(transaction);
            }
        }
    }

    protected Boolean _transactionOutputExists(final TransactionOutputIdentifier transactionOutputIdentifier, final FullNodeDatabaseManager databaseManager) throws DatabaseException {
        final UnconfirmedTransactionOutputDatabaseManager unconfirmedTransactionOutputDatabaseManager = databaseManager.getUnconfirmedTransactionOutputDatabaseManager();
        final UnconfirmedTransactionOutputId unconfirmedTransactionOutputId = unconfirmedTransactionOutputDatabaseManager.findUnconfirmedTransactionOutput(transactionOutputIdentifier);
        if (unconfirmedTransactionOutputId != null) { return true; }

        final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
        final TransactionOutput transactionOutput = transactionDatabaseManager.getTransactionOutput(transactionOutputIdentifier);
        return (transactionOutput != null);
    }

    public OrphanedTransactionsCache() { }

    public synchronized void add(final Transaction transaction, final FullNodeDatabaseManager databaseManager) throws DatabaseException {
        final boolean transactionIsUnique = _orphanedTransactionSet.add(transaction);
        if (! transactionIsUnique) { return; }

        Logger.debug("Queuing orphaned Transaction: " + transaction.getHash() + " (" + _orphanedTransactionSet.size() + " / " + MAX_ORPHANED_TRANSACTION_COUNT + ")");

        _orphanedTransactionsByAge.addLast(transaction);
        if (_orphanedTransactionSet.size() > MAX_ORPHANED_TRANSACTION_COUNT) {
            _purgeOldTransactions();
        }

        for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
            final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
            final Boolean transactionOutputBeingSpentExists = _transactionOutputExists(transactionOutputIdentifier, databaseManager);
            if (! transactionOutputBeingSpentExists) {
                if (! _orphanedTransactions.containsKey(transactionOutputIdentifier)) {
                    _orphanedTransactions.put(transactionOutputIdentifier, new HashSet<Transaction>());
                }

                final HashSet<Transaction> queuedTransactions = _orphanedTransactions.get(transactionOutputIdentifier);
                queuedTransactions.add(transaction);
            }
        }
    }

    public synchronized Set<Transaction> onTransactionAdded(final Transaction transaction) {
        final HashSet<Transaction> possiblyEligibleTransactions = new HashSet<Transaction>();

        final Sha256Hash transactionHash = transaction.getHash();

        for (final TransactionOutput transactionOutput : transaction.getTransactionOutputs()) {
            final Integer transactionOutputIndex = transactionOutput.getIndex();
            final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, transactionOutputIndex);
            final HashSet<Transaction> queuedTransactions = _orphanedTransactions.remove(transactionOutputIdentifier);
            if (queuedTransactions == null) { continue; }

            possiblyEligibleTransactions.addAll(queuedTransactions);

            Logger.debug("Promoting orphaned Transaction: " + transaction.getHash());
        }

        if (_orphanedTransactionSet.contains(transaction)) {
            _removeOrphanedTransaction(transaction);
        }

        return possiblyEligibleTransactions;
    }
}
