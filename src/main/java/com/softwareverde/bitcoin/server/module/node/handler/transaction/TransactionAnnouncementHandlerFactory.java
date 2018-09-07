package com.softwareverde.bitcoin.server.module.node.handler.transaction;

import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.server.module.node.NodeInitializer;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.network.time.NetworkTime;

public class TransactionAnnouncementHandlerFactory implements NodeInitializer.TransactionsAnnouncementCallbackFactory {
    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;
    protected final NetworkTime _networkTime;
    protected final MedianBlockTime _medianBlockTime;

    public TransactionAnnouncementHandlerFactory(final MysqlDatabaseConnectionFactory databaseConnectionFactory, final NetworkTime networkTime, final MedianBlockTime medianBlockTime) {
        _databaseConnectionFactory = databaseConnectionFactory;
        _networkTime = networkTime;
        _medianBlockTime = medianBlockTime;
    }

    @Override
    public BitcoinNode.TransactionsAnnouncementCallback createTransactionsAnnouncementCallback(final BitcoinNode bitcoinNode) {
        return new TransactionsAnnouncementHandler(bitcoinNode, _databaseConnectionFactory, _networkTime, _medianBlockTime);
    }
}
