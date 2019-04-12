package com.softwareverde.bitcoin.server.module.node.handler.transaction;

import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.module.node.database.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.io.Logger;

public class QueryUnconfirmedTransactionsHandler implements BitcoinNode.QueryUnconfirmedTransactionsCallback {
    public static final BitcoinNode.QueryUnconfirmedTransactionsCallback IGNORE_REQUESTS_HANDLER = new BitcoinNode.QueryUnconfirmedTransactionsCallback() {
        @Override
        public void run(final BitcoinNode bitcoinNode) { }
    };

    protected final DatabaseConnectionFactory _databaseConnectionFactory;
    protected final DatabaseManagerCache _databaseManagerCache;

    public QueryUnconfirmedTransactionsHandler(final DatabaseConnectionFactory databaseConnectionFactory, final DatabaseManagerCache databaseManagerCache) {
        _databaseConnectionFactory = databaseConnectionFactory;
        _databaseManagerCache = databaseManagerCache;
    }

    @Override
    public void run(final BitcoinNode bitcoinNode) {
        try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection, _databaseManagerCache);
            final List<TransactionId> unconfirmedTransactionIds = transactionDatabaseManager.getUnconfirmedTransactionIds();

            final ImmutableListBuilder<Sha256Hash> unconfirmedTransactionHashes = new ImmutableListBuilder<Sha256Hash>(unconfirmedTransactionIds.getSize());

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
            Logger.log(exception);
        }
    }
}
