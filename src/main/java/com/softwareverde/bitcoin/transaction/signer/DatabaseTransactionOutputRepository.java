package com.softwareverde.bitcoin.transaction.signer;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.module.node.database.TransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.io.Logger;

public class DatabaseTransactionOutputRepository implements TransactionOutputRepository {
    protected final DatabaseConnection _databaseConnection;
    protected final DatabaseManagerCache _databaseCache;

    public DatabaseTransactionOutputRepository(final DatabaseConnection databaseConnection, final DatabaseManagerCache databaseCache) {
        _databaseConnection = databaseConnection;
        _databaseCache = databaseCache;
    }

    @Override
    public TransactionOutput get(final TransactionOutputIdentifier transactionOutputIdentifier) {
        try {
            final TransactionOutputDatabaseManager transactionOutputDatabaseManager = new TransactionOutputDatabaseManager(_databaseConnection, _databaseCache);
            final TransactionOutputId transactionOutputId = transactionOutputDatabaseManager.findTransactionOutput(transactionOutputIdentifier);
            if (transactionOutputId == null) { return null; }

            return transactionOutputDatabaseManager.getTransactionOutput(transactionOutputId);
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return null;
        }
    }
}
