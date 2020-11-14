package com.softwareverde.bitcoin.server.module.node.handler.transaction;

import com.softwareverde.bitcoin.server.SynchronizationStatus;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.node.fullnode.FullNodeBitcoinNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.pending.PendingTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;

public class TransactionInventoryAnnouncementHandler implements BitcoinNode.TransactionInventoryAnnouncementHandler {
    public static final BitcoinNode.TransactionInventoryAnnouncementHandler IGNORE_NEW_TRANSACTIONS_HANDLER = new BitcoinNode.TransactionInventoryAnnouncementHandler() {
        @Override
        public void onResult(final BitcoinNode bitcoinNode, final List<Sha256Hash> result) { }
    };

    protected final BitcoinNode _bitcoinNode;
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final Runnable _newInventoryCallback;
    protected final SynchronizationStatus _synchronizationStatus;

    public TransactionInventoryAnnouncementHandler(final BitcoinNode bitcoinNode, final FullNodeDatabaseManagerFactory databaseManagerFactory, final SynchronizationStatus synchronizationStatus, final Runnable newInventoryCallback) {
        _bitcoinNode = bitcoinNode;
        _databaseManagerFactory = databaseManagerFactory;
        _newInventoryCallback = newInventoryCallback;
        _synchronizationStatus = synchronizationStatus;
    }

    @Override
    public void onResult(final BitcoinNode bitcoinNode, final List<Sha256Hash> transactionHashes) {
        if (_synchronizationStatus != null) {
            final Boolean isReadyForTransactions = _synchronizationStatus.isReadyForTransactions();
            if (! isReadyForTransactions) { return; }
        }

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
