package com.softwareverde.bitcoin.server.module.node.handler;

import com.softwareverde.bitcoin.server.module.node.MemoryPoolEnquirer;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.bloomfilter.BloomFilter;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;

public class MemoryPoolEnquirerHandler implements MemoryPoolEnquirer {
    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;

    public MemoryPoolEnquirerHandler(final MysqlDatabaseConnectionFactory databaseConnectionFactory) {
        _databaseConnectionFactory = databaseConnectionFactory;
    }

    @Override
    public BloomFilter getBloomFilter(final Sha256Hash blockHash) {
        return null; // TODO
    }

    @Override
    public Integer getMemoryPoolTransactionCount() {
        return null; // TODO
    }

    @Override
    public Transaction getTransaction(final Sha256Hash transactionHash) {
        return null; // TODO
    }
}
