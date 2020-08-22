package com.softwareverde.bitcoin.server.module.node.handler.transaction;

import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.node.fullnode.FullNodeBitcoinNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.pending.PendingTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.sync.transaction.pending.PendingTransactionId;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.security.hash.sha256.Sha256Hash;

public class TransactionInventoryMessageHandler implements BitcoinNode.TransactionInventoryMessageCallback {
    public static final BitcoinNode.TransactionInventoryMessageCallback IGNORE_NEW_TRANSACTIONS_HANDLER = new BitcoinNode.TransactionInventoryMessageCallback() {
        @Override
        public void onResult(final List<Sha256Hash> result) { }
    };

    protected final BitcoinNode _bitcoinNode;
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final Runnable _newInventoryCallback;

    public TransactionInventoryMessageHandler(final BitcoinNode bitcoinNode, final FullNodeDatabaseManagerFactory databaseManagerFactory, final Runnable newInventoryCallback) {
        _bitcoinNode = bitcoinNode;
        _databaseManagerFactory = databaseManagerFactory;
        _newInventoryCallback = newInventoryCallback;
    }

    @Override
    public void onResult(final List<Sha256Hash> transactionHashes) {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final TransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
            final PendingTransactionDatabaseManager pendingTransactionDatabaseManager = databaseManager.getPendingTransactionDatabaseManager();
            final FullNodeBitcoinNodeDatabaseManager nodeDatabaseManager = databaseManager.getNodeDatabaseManager();

            final List<Sha256Hash> unseenTransactionHashes;
            {
                final ImmutableListBuilder<Sha256Hash> unseenTransactionHashesBuilder = new ImmutableListBuilder<Sha256Hash>(transactionHashes.getCount());
                for (final Sha256Hash transactionHash : transactionHashes) {
                    final TransactionId transactionId = transactionDatabaseManager.getTransactionId(transactionHash);
                    if (transactionId == null) {
                        unseenTransactionHashesBuilder.add(transactionHash);
                    }
                }
                unseenTransactionHashes = unseenTransactionHashesBuilder.build();
            }

            if (! unseenTransactionHashes.isEmpty()) {
                pendingTransactionDatabaseManager.storeTransactionHashes(unseenTransactionHashes);
                nodeDatabaseManager.updateTransactionInventory(_bitcoinNode, unseenTransactionHashes);

                if (_newInventoryCallback != null) {
                    _newInventoryCallback.run();
                }
            }
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
        }
    }
}