package com.softwareverde.bitcoin.server.module.node.handler;

import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;

public class SpvUnconfirmedTransactionsHandler {
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;

    public SpvUnconfirmedTransactionsHandler(final FullNodeDatabaseManagerFactory databaseManagerFactory) {
        _databaseManagerFactory = databaseManagerFactory;
    }

    public void broadcastUnconfirmedTransactions(final BitcoinNode bitcoinNode) {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
            final MutableList<Sha256Hash> matchedTransactionHashes = new MutableList<Sha256Hash>();

            final List<TransactionId> transactionIds = transactionDatabaseManager.getUnconfirmedTransactionIds();
            for (final TransactionId transactionId : transactionIds) {
                final Transaction transaction = transactionDatabaseManager.getTransaction(transactionId);
                final boolean matchesFilter = bitcoinNode.matchesFilter(transaction);
                if (matchesFilter) {
                    matchedTransactionHashes.add(transaction.getHash());
                }
            }

            if (! matchedTransactionHashes.isEmpty()) {
                bitcoinNode.transmitTransactionHashes(matchedTransactionHashes);
            }
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
        }
    }
}
