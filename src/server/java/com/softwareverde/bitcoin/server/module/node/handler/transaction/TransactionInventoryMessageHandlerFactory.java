package com.softwareverde.bitcoin.server.module.node.handler.transaction;

import com.softwareverde.bitcoin.server.SynchronizationStatus;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.manager.NodeInitializer;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.CircleBuffer;

public class TransactionInventoryMessageHandlerFactory implements NodeInitializer.TransactionsAnnouncementHandlerFactory {
    public static final TransactionInventoryMessageHandlerFactory IGNORE_NEW_TRANSACTIONS_HANDLER_FACTORY = new TransactionInventoryMessageHandlerFactory(null, null, null) {
        @Override
        public BitcoinNode.TransactionInventoryAnnouncementHandler createTransactionsAnnouncementHandler(final BitcoinNode bitcoinNode) {
            return TransactionInventoryAnnouncementHandler.IGNORE_NEW_TRANSACTIONS_HANDLER;
        }
    };

    protected final CircleBuffer<Sha256Hash> _recentlyAnnouncedTransactionHashes = new CircleBuffer<>(1024);

    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final SynchronizationStatus _synchronizationStatus;
    protected final Runnable _newInventoryCallback;

    public TransactionInventoryMessageHandlerFactory(final FullNodeDatabaseManagerFactory databaseManagerFactory, final SynchronizationStatus synchronizationStatus, final Runnable newInventoryCallback) {
        _databaseManagerFactory = databaseManagerFactory;
        _synchronizationStatus = synchronizationStatus;
        _newInventoryCallback = newInventoryCallback;
    }

    @Override
    public BitcoinNode.TransactionInventoryAnnouncementHandler createTransactionsAnnouncementHandler(final BitcoinNode bitcoinNode) {
        return new TransactionInventoryAnnouncementHandler(bitcoinNode, _databaseManagerFactory, _synchronizationStatus, _newInventoryCallback, _recentlyAnnouncedTransactionHashes);
    }
}
