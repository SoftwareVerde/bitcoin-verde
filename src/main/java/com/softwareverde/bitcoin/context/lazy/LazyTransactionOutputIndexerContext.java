package com.softwareverde.bitcoin.context.lazy;

import com.softwareverde.bitcoin.context.AtomicTransactionOutputIndexerContext;
import com.softwareverde.bitcoin.context.ContextException;
import com.softwareverde.bitcoin.context.IndexerCache;
import com.softwareverde.bitcoin.context.TransactionOutputIndexerContext;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.indexer.BlockchainIndexerDatabaseManager;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.LogLevel;
import com.softwareverde.logging.Logger;

public class LazyTransactionOutputIndexerContext implements TransactionOutputIndexerContext {
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final IndexerCache _indexerCache;

    protected FullNodeDatabaseManager _databaseManager;

    public LazyTransactionOutputIndexerContext(final FullNodeDatabaseManagerFactory databaseManagerFactory, final IndexerCache indexerCache) {
        _databaseManagerFactory = databaseManagerFactory;
        _indexerCache = indexerCache;
    }

    @Override
    public AtomicTransactionOutputIndexerContext newTransactionOutputIndexerContext() throws ContextException {
        FullNodeDatabaseManager databaseManager = null;
        try {
            final Integer cacheIdentifier = _indexerCache.newCacheIdentifier();
            databaseManager = _databaseManagerFactory.newDatabaseManager();
            return new LazyAtomicTransactionOutputIndexerContext(databaseManager, _indexerCache, cacheIdentifier);
        }
        catch (final Exception exception) {
            try {
                if (databaseManager != null) {
                    databaseManager.close();
                }
            }
            catch (final DatabaseException databaseException) {
                exception.addSuppressed(databaseException);
            }

            throw new ContextException(exception);
        }
    }

    @Override
    public void commitLastProcessedTransactionId(final TransactionId transactionId) throws ContextException {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockchainIndexerDatabaseManager blockchainIndexerDatabaseManager = databaseManager.getBlockchainIndexerDatabaseManager();
            blockchainIndexerDatabaseManager.markTransactionProcessed(transactionId);

            if (Logger.isTraceEnabled()) {
                _indexerCache.debug(LogLevel.TRACE);
            }
        }
        catch (final Exception exception) {
            throw new ContextException(exception);
        }
    }
}
