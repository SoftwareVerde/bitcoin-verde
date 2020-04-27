package com.softwareverde.bitcoin.server.module.node.rpc.handler;

import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.UtxoDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.rpc.NodeRpcHandler;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.timer.MilliTimer;

public class UtxoCacheHandler implements NodeRpcHandler.UtxoCacheHandler {
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;

    public UtxoCacheHandler(final FullNodeDatabaseManagerFactory databaseManagerFactory) {
        _databaseManagerFactory = databaseManagerFactory;
    }

    @Override
    public Long getCachedUtxoCount() {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
            return transactionDatabaseManager.getCachedUnspentTransactionOutputCount();
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
            return null;
        }
    }

    @Override
    public Long getMaxCachedUtxoCount() {
        return UtxoDatabaseManager.DEFAULT_MAX_UTXO_CACHE_COUNT;
    }

    @Override
    public Long getUncommittedUtxoCount() {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
            return transactionDatabaseManager.getUncommittedUnspentTransactionOutputCount();
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
            return null;
        }
    }

    @Override
    public Long getCommittedUtxoBlockHeight() {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
            return transactionDatabaseManager.getCommittedUnspentTransactionOutputBlockHeight();
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
            return null;
        }
    }

    @Override
    public void commitUtxoCache() {
        final DatabaseConnectionFactory databaseConnectionFactory = _databaseManagerFactory.getDatabaseConnectionFactory();
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
            final MilliTimer utxoCommitTimer = new MilliTimer();
            utxoCommitTimer.start();

            transactionDatabaseManager.commitUnspentTransactionOutputs(databaseConnectionFactory);

            utxoCommitTimer.stop();
            Logger.debug("Commit Timer: " + utxoCommitTimer.getMillisecondsElapsed() + "ms.");
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
        }
    }
}
