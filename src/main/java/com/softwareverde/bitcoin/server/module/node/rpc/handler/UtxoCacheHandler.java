package com.softwareverde.bitcoin.server.module.node.rpc.handler;

import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.rpc.NodeRpcHandler;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;

public class UtxoCacheHandler implements NodeRpcHandler.UtxoCacheHandler {
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;

    public UtxoCacheHandler(final FullNodeDatabaseManagerFactory databaseManagerFactory) {
        _databaseManagerFactory = databaseManagerFactory;
    }

    @Override
    public Long getCachedUtxoCount() {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = databaseManager.getUnspentTransactionOutputDatabaseManager();
            return unspentTransactionOutputDatabaseManager.getCachedUnspentTransactionOutputCount();
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
            return null;
        }
    }

    @Override
    public Long getMaxCachedUtxoCount() {
        return UnspentTransactionOutputDatabaseManager.DEFAULT_MAX_UTXO_CACHE_COUNT;
    }

    @Override
    public Long getUncommittedUtxoCount() {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = databaseManager.getUnspentTransactionOutputDatabaseManager();
            return unspentTransactionOutputDatabaseManager.getUncommittedUnspentTransactionOutputCount();
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
            return null;
        }
    }

    @Override
    public Long getCommittedUtxoBlockHeight() {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = databaseManager.getUnspentTransactionOutputDatabaseManager();
            return unspentTransactionOutputDatabaseManager.getCommittedUnspentTransactionOutputBlockHeight();
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
            final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = databaseManager.getUnspentTransactionOutputDatabaseManager();
            unspentTransactionOutputDatabaseManager.commitUnspentTransactionOutputs(databaseConnectionFactory);
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
        }
    }
}
