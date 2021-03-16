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
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.CircleBuffer;

public class TransactionInventoryAnnouncementHandler implements BitcoinNode.TransactionInventoryAnnouncementHandler {
    public static final BitcoinNode.TransactionInventoryAnnouncementHandler IGNORE_NEW_TRANSACTIONS_HANDLER = new BitcoinNode.TransactionInventoryAnnouncementHandler() {
        @Override
        public void onResult(final BitcoinNode bitcoinNode, final List<Sha256Hash> result) { }
    };

    protected final BitcoinNode _bitcoinNode;
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final Runnable _newInventoryCallback;
    protected final SynchronizationStatus _synchronizationStatus;

    // The cache is intended to be used across multiple threads and peers, and is essentially a global state.
    protected final CircleBuffer<Sha256Hash> _recentlyAnnouncedTransactionHashCache;

    public TransactionInventoryAnnouncementHandler(final BitcoinNode bitcoinNode, final FullNodeDatabaseManagerFactory databaseManagerFactory, final SynchronizationStatus synchronizationStatus, final Runnable newInventoryCallback, final CircleBuffer<Sha256Hash> recentlyAnnouncedTransactionHashCache) {
        _bitcoinNode = bitcoinNode;
        _databaseManagerFactory = databaseManagerFactory;
        _newInventoryCallback = newInventoryCallback;
        _synchronizationStatus = synchronizationStatus;
        _recentlyAnnouncedTransactionHashCache = recentlyAnnouncedTransactionHashCache;
    }

    @Override
    public void onResult(final BitcoinNode bitcoinNode, final List<Sha256Hash> transactionHashes) {
        if (_synchronizationStatus != null) {
            final Boolean isReadyForTransactions = _synchronizationStatus.isReadyForTransactions();
            if (! isReadyForTransactions) { return; }
        }

        // Handle global recency caching for TransactionHashes and skip transactions that have already been announced by other peers.
        final MutableList<Sha256Hash> cacheMissTransactionHashes = new MutableList<>(transactionHashes.getCount());
        synchronized (_recentlyAnnouncedTransactionHashCache) {
            for (final Sha256Hash transactionHash : transactionHashes) {
                final boolean hasBeenSeen = _recentlyAnnouncedTransactionHashCache.contains(transactionHash);
                if (hasBeenSeen) { continue; }

                cacheMissTransactionHashes.add(transactionHash);
            }

            _recentlyAnnouncedTransactionHashCache.pushAll(cacheMissTransactionHashes);
        }
        if (cacheMissTransactionHashes.isEmpty()) { return; }

        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final TransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
            final PendingTransactionDatabaseManager pendingTransactionDatabaseManager = databaseManager.getPendingTransactionDatabaseManager();
            final FullNodeBitcoinNodeDatabaseManager nodeDatabaseManager = databaseManager.getNodeDatabaseManager();

            final List<Sha256Hash> unseenTransactionHashes;
            {
                final ImmutableListBuilder<Sha256Hash> unseenTransactionHashesBuilder = new ImmutableListBuilder<Sha256Hash>(cacheMissTransactionHashes.getCount());
                for (final Sha256Hash transactionHash : cacheMissTransactionHashes) {
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
