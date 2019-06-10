package com.softwareverde.bitcoin.transaction.signer;

import com.softwareverde.bitcoin.server.module.node.database.core.CoreDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.output.TransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.io.Logger;

public class DatabaseTransactionOutputRepository implements TransactionOutputRepository {
    protected final CoreDatabaseManager _databaseManager;

    public DatabaseTransactionOutputRepository(final CoreDatabaseManager databaseManager) {
        _databaseManager = databaseManager;
    }

    @Override
    public TransactionOutput get(final TransactionOutputIdentifier transactionOutputIdentifier) {
        try {
            final TransactionOutputDatabaseManager transactionOutputDatabaseManager = _databaseManager.getTransactionOutputDatabaseManager();

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
