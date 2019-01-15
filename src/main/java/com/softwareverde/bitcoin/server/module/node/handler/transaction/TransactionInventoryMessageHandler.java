package com.softwareverde.bitcoin.server.module.node.handler.transaction;

import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.database.PendingTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.database.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.sync.transaction.pending.PendingTransactionId;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.io.Logger;

public class TransactionInventoryMessageHandler implements BitcoinNode.TransactionInventoryMessageCallback {
    public static final BitcoinNode.TransactionInventoryMessageCallback IGNORE_NEW_TRANSACTIONS_HANDLER = new BitcoinNode.TransactionInventoryMessageCallback() {
        @Override
        public void onResult(final List<Sha256Hash> result) { }
    };

    protected final BitcoinNode _bitcoinNode;
    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;
    protected final DatabaseManagerCache _databaseManagerCache;
    protected final Runnable _newInventoryCallback;

    public TransactionInventoryMessageHandler(final BitcoinNode bitcoinNode, final MysqlDatabaseConnectionFactory databaseConnectionFactory, final DatabaseManagerCache databaseManagerCache, final Runnable newInventoryCallback) {
        _bitcoinNode = bitcoinNode;
        _databaseConnectionFactory = databaseConnectionFactory;
        _databaseManagerCache = databaseManagerCache;
        _newInventoryCallback = newInventoryCallback;
    }

    @Override
    public void onResult(final List<Sha256Hash> transactionHashes) {
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection, _databaseManagerCache);

            final List<Sha256Hash> unseenTransactionHashes;
            {
                final ImmutableListBuilder<Sha256Hash> unseenTransactionHashesBuilder = new ImmutableListBuilder<Sha256Hash>(transactionHashes.getSize());
                for (final Sha256Hash transactionHash : transactionHashes) {
                    final TransactionId transactionId = transactionDatabaseManager.getTransactionId(transactionHash);
                    if (transactionId == null) {
                        unseenTransactionHashesBuilder.add(transactionHash);
                    }
                }
                unseenTransactionHashes = unseenTransactionHashesBuilder.build();
            }

            if (! unseenTransactionHashes.isEmpty()) {
                final PendingTransactionDatabaseManager pendingTransactionDatabaseManager = new PendingTransactionDatabaseManager(databaseConnection);
                final List<PendingTransactionId> pendingTransactionIds = pendingTransactionDatabaseManager.storeTransactionHashes(unseenTransactionHashes);

                final BitcoinNodeDatabaseManager nodeDatabaseManager = new BitcoinNodeDatabaseManager(databaseConnection);
                nodeDatabaseManager.updateTransactionInventory(_bitcoinNode, pendingTransactionIds);

                final Runnable newInventoryCallback = _newInventoryCallback;
                if (newInventoryCallback != null) {
                    newInventoryCallback.run();
                }
            }
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
        }
    }
}