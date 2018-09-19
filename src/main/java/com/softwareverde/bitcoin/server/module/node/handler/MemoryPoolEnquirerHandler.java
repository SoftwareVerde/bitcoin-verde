package com.softwareverde.bitcoin.server.module.node.handler;

import com.softwareverde.bitcoin.server.database.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.MemoryPoolEnquirer;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.bloomfilter.BloomFilter;
import com.softwareverde.bloomfilter.MutableBloomFilter;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.io.Logger;

public class MemoryPoolEnquirerHandler implements MemoryPoolEnquirer {
    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;

    public MemoryPoolEnquirerHandler(final MysqlDatabaseConnectionFactory databaseConnectionFactory) {
        _databaseConnectionFactory = databaseConnectionFactory;
    }

    @Override
    public BloomFilter getBloomFilter(final Sha256Hash blockHash) {
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection);
            final List<TransactionId> transactionIds = transactionDatabaseManager.getTransactionIdsFromMemoryPool();

            final MutableBloomFilter bloomFilter = new MutableBloomFilter(transactionIds.getSize(), 0.01D);

            for (final TransactionId transactionId : transactionIds) {
                final Sha256Hash transactionHash = transactionDatabaseManager.getTransactionHash(transactionId);
                bloomFilter.addItem(transactionHash);
            }

            return bloomFilter;
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
        }

        return BloomFilter.MATCH_NONE;
    }

    @Override
    public Integer getMemoryPoolTransactionCount() {
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection);
            return transactionDatabaseManager.getMemoryPoolTransactionCount();
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
        }

        return 0;
    }

    @Override
    public Transaction getTransaction(final Sha256Hash transactionHash) {
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection);
            final TransactionId transactionId = transactionDatabaseManager.getTransactionIdFromHash(transactionHash);
            if (transactionId == null) { return null; }

            return transactionDatabaseManager.getTransaction(transactionId);
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
        }

        return null;
    }
}
