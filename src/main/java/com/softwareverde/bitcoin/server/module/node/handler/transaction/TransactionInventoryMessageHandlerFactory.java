package com.softwareverde.bitcoin.server.module.node.handler.transaction;

import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.module.node.NodeInitializer;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.network.time.NetworkTime;

public class TransactionInventoryMessageHandlerFactory implements NodeInitializer.TransactionsAnnouncementCallbackFactory {
    public static final TransactionInventoryMessageHandlerFactory IGNORE_NEW_TRANSACTIONS_HANDLER_FACTORY = new TransactionInventoryMessageHandlerFactory(null, null, null, null) {
        @Override
        public BitcoinNode.TransactionInventoryMessageCallback createTransactionsAnnouncementCallback(final BitcoinNode bitcoinNode) {
            return TransactionInventoryMessageHandler.IGNORE_NEW_TRANSACTIONS_HANDLER;
        }
    };

    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;
    protected final DatabaseManagerCache _databaseManagerCache;
    protected final NetworkTime _networkTime;
    protected final MedianBlockTime _medianBlockTime;

    public TransactionInventoryMessageHandlerFactory(final MysqlDatabaseConnectionFactory databaseConnectionFactory, final DatabaseManagerCache databaseManagerCache, final NetworkTime networkTime, final MedianBlockTime medianBlockTime) {
        _databaseConnectionFactory = databaseConnectionFactory;
        _databaseManagerCache = databaseManagerCache;
        _networkTime = networkTime;
        _medianBlockTime = medianBlockTime;
    }

    @Override
    public BitcoinNode.TransactionInventoryMessageCallback createTransactionsAnnouncementCallback(final BitcoinNode bitcoinNode) {
        return new TransactionInventoryMessageHandler(bitcoinNode, _databaseConnectionFactory, _databaseManagerCache, _networkTime, _medianBlockTime);
    }
}
