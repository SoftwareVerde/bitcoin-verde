package com.softwareverde.bitcoin.server.module.node.handler.transaction;

import com.softwareverde.async.ConcurrentHashSet;
import com.softwareverde.bitcoin.server.database.TransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.io.Logger;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class OrphanedTransactionsCache {
    protected final ConcurrentHashMap<TransactionOutputIdentifier, ConcurrentHashSet<Transaction>> _orphanedTransactions = new ConcurrentHashMap<TransactionOutputIdentifier, ConcurrentHashSet<Transaction>>(1024);
    protected final DatabaseManagerCache _databaseManagerCache;

    public OrphanedTransactionsCache(final DatabaseManagerCache databaseManagerCache) {
        _databaseManagerCache = databaseManagerCache;
    }

    public void add(final Transaction transaction, final MysqlDatabaseConnection databaseConnection) throws DatabaseException {
        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = new TransactionOutputDatabaseManager(databaseConnection, _databaseManagerCache);

        for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
            final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
            final TransactionOutputId transactionOutputId = transactionOutputDatabaseManager.findTransactionOutput(transactionOutputIdentifier);
            if (transactionOutputId == null) {
                if (!_orphanedTransactions.containsKey(transactionOutputIdentifier)) {
                    _orphanedTransactions.put(transactionOutputIdentifier, new ConcurrentHashSet<Transaction>());
                }

                final ConcurrentHashSet<Transaction> queuedTransactions = _orphanedTransactions.get(transactionOutputIdentifier);
                final Boolean isUnique = queuedTransactions.add(transaction);
                if (isUnique) {
                    Logger.log("Queuing Orphaned Transaction: " + transaction.getHash());
                }
            }
        }
    }

    public Set<Transaction> onTransactionAdded(final Transaction transaction) {
        final HashSet<Transaction> possiblyEligibleTransactions = new HashSet<Transaction>();

        final Sha256Hash transactionHash = transaction.getHash();
        for (final TransactionOutput transactionOutput : transaction.getTransactionOutputs()) {
            final Integer transactionOutputIndex = transactionOutput.getIndex();
            final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, transactionOutputIndex);
            final ConcurrentHashSet<Transaction> queuedTransactions = _orphanedTransactions.remove(transactionOutputIdentifier);
            if (queuedTransactions == null) { continue; }

            possiblyEligibleTransactions.addAll(queuedTransactions);

            Logger.log("Promoting Orphaned Transaction: " + transaction.getHash());
        }

        return possiblyEligibleTransactions;
    }
}
