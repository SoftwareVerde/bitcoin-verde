package com.softwareverde.bitcoin.server.module.node.handler.transaction;

import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.module.node.manager.NodeInitializer;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;

public class TransactionInventoryMessageHandlerFactory implements NodeInitializer.TransactionsAnnouncementCallbackFactory {
    public static final TransactionInventoryMessageHandlerFactory IGNORE_NEW_TRANSACTIONS_HANDLER_FACTORY = new TransactionInventoryMessageHandlerFactory(null, null, null) {
        @Override
        public BitcoinNode.TransactionInventoryMessageCallback createTransactionsAnnouncementCallback(final BitcoinNode bitcoinNode) {
            return TransactionInventoryMessageHandler.IGNORE_NEW_TRANSACTIONS_HANDLER;
        }
    };

    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;
    protected final DatabaseManagerCache _databaseManagerCache;
    protected final Runnable _newInventoryCallback;

    public TransactionInventoryMessageHandlerFactory(final MysqlDatabaseConnectionFactory databaseConnectionFactory, final DatabaseManagerCache databaseManagerCache, final Runnable newInventoryCallback) {
        _databaseConnectionFactory = databaseConnectionFactory;
        _databaseManagerCache = databaseManagerCache;
        _newInventoryCallback = newInventoryCallback;
    }

    @Override
    public BitcoinNode.TransactionInventoryMessageCallback createTransactionsAnnouncementCallback(final BitcoinNode bitcoinNode) {
        return new TransactionInventoryMessageHandler(bitcoinNode, _databaseConnectionFactory, _databaseManagerCache, _newInventoryCallback);
    }
}
