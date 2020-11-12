package com.softwareverde.bitcoin.server.module.node.handler.transaction;

import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;

public class QueryUnconfirmedTransactionsHandler implements BitcoinNode.RequestUnconfirmedTransactionsHandler {
    public static final BitcoinNode.RequestUnconfirmedTransactionsHandler IGNORE_REQUESTS_HANDLER = new BitcoinNode.RequestUnconfirmedTransactionsHandler() {
        @Override
        public void run(final BitcoinNode bitcoinNode) { }
    };

    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;

    public QueryUnconfirmedTransactionsHandler(final FullNodeDatabaseManagerFactory databaseManagerFactory) {
        _databaseManagerFactory = databaseManagerFactory;
    }

    @Override
    public void run(final BitcoinNode bitcoinNode) {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
            final List<TransactionId> unconfirmedTransactionIds = transactionDatabaseManager.getUnconfirmedTransactionIds();

            final ImmutableListBuilder<Sha256Hash> unconfirmedTransactionHashes = new ImmutableListBuilder<Sha256Hash>(unconfirmedTransactionIds.getCount());

            if (bitcoinNode.hasBloomFilter()) {
                for (final TransactionId transactionId : unconfirmedTransactionIds) {
                    final Transaction transaction = transactionDatabaseManager.getTransaction(transactionId);
                    if ( (transaction != null) && (bitcoinNode.matchesFilter(transaction)) ) {
                        unconfirmedTransactionHashes.add(transaction.getHash());
                    }
                }
            }
            else {
                for (final TransactionId transactionId : unconfirmedTransactionIds) {
                    unconfirmedTransactionHashes.add(transactionDatabaseManager.getTransactionHash(transactionId));
                }
            }

            if (unconfirmedTransactionHashes.getCount() > 0) {
                bitcoinNode.transmitTransactionHashes(unconfirmedTransactionHashes.build());
            }
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
        }
    }
}
