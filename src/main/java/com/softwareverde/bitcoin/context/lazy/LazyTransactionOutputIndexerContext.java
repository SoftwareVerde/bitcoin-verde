package com.softwareverde.bitcoin.context.lazy;

import com.softwareverde.bitcoin.context.AtomicTransactionOutputIndexerContext;
import com.softwareverde.bitcoin.context.ContextException;
import com.softwareverde.bitcoin.context.TransactionOutputIndexerContext;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.database.DatabaseException;

public class LazyTransactionOutputIndexerContext implements TransactionOutputIndexerContext {
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;

    protected FullNodeDatabaseManager _databaseManager;

    public LazyTransactionOutputIndexerContext(final FullNodeDatabaseManagerFactory databaseManagerFactory) {
        _databaseManagerFactory = databaseManagerFactory;
    }

    @Override
    public AtomicTransactionOutputIndexerContext newTransactionOutputIndexerContext() throws ContextException {
        FullNodeDatabaseManager databaseManager = null;
        try {
            databaseManager = _databaseManagerFactory.newDatabaseManager();
            return new LazyAtomicTransactionOutputIndexerContext(databaseManager);
        }
        catch (final Exception databaseException) {
            try {
                if (databaseManager != null) {
                    databaseManager.close();
                }
            }
            catch (final DatabaseException exception) {
                databaseException.addSuppressed(exception);
            }

            throw new ContextException(databaseException);
        }
    }
}
