package com.softwareverde.bitcoin.server.module.node.handler.transaction;

import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.manager.NodeInitializer;
import com.softwareverde.bitcoin.server.node.BitcoinNode;

public class TransactionInventoryMessageHandlerFactory implements NodeInitializer.TransactionsAnnouncementCallbackFactory {
    public static final TransactionInventoryMessageHandlerFactory IGNORE_NEW_TRANSACTIONS_HANDLER_FACTORY = new TransactionInventoryMessageHandlerFactory(null, null) {
        @Override
        public BitcoinNode.TransactionInventoryMessageCallback createTransactionsAnnouncementCallback(final BitcoinNode bitcoinNode) {
            return TransactionInventoryMessageHandler.IGNORE_NEW_TRANSACTIONS_HANDLER;
        }
    };

    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final Runnable _newInventoryCallback;

    public TransactionInventoryMessageHandlerFactory(final FullNodeDatabaseManagerFactory databaseManagerFactory, final Runnable newInventoryCallback) {
        _databaseManagerFactory = databaseManagerFactory;
        _newInventoryCallback = newInventoryCallback;
    }

    @Override
    public BitcoinNode.TransactionInventoryMessageCallback createTransactionsAnnouncementCallback(final BitcoinNode bitcoinNode) {
        return new TransactionInventoryMessageHandler(bitcoinNode, _databaseManagerFactory, _newInventoryCallback);
    }
}
