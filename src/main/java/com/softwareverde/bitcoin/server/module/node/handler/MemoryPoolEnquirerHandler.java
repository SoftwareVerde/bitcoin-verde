package com.softwareverde.bitcoin.server.module.node.handler;

import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.module.node.MemoryPoolEnquirer;
import com.softwareverde.bitcoin.server.module.node.database.core.CoreDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.core.CoreDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.transaction.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.core.CoreTransactionDatabaseManager;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bloomfilter.BloomFilter;
import com.softwareverde.bloomfilter.MutableBloomFilter;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.io.Logger;

public class MemoryPoolEnquirerHandler implements MemoryPoolEnquirer {
    protected final CoreDatabaseManagerFactory _databaseManagerFactory;

    public MemoryPoolEnquirerHandler(final CoreDatabaseManagerFactory databaseManagerFactory) {
        _databaseManagerFactory = databaseManagerFactory;
    }

    @Override
    public BloomFilter getBloomFilter(final Sha256Hash blockHash) {
        try (final CoreDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final CoreTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
            final List<TransactionId> transactionIds = transactionDatabaseManager.getUnconfirmedTransactionIds();

            final MutableBloomFilter bloomFilter = MutableBloomFilter.newInstance((long) transactionIds.getSize(), 0.01D);

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
        try (final CoreDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final CoreTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
            return transactionDatabaseManager.getUnconfirmedTransactionCount();
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
        }

        return 0;
    }

    @Override
    public Transaction getTransaction(final Sha256Hash transactionHash) {
        try (final CoreDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final TransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
            final TransactionId transactionId = transactionDatabaseManager.getTransactionId(transactionHash);
            if (transactionId == null) { return null; }

            return transactionDatabaseManager.getTransaction(transactionId);
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
        }

        return null;
    }
}
