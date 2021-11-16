package com.softwareverde.bitcoin.server.module.node.rpc.handler;

import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.CommitAsyncMode;
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
            return unspentTransactionOutputDatabaseManager.getUncommittedUnspentTransactionOutputCount(true);
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
            return null;
        }
    }

    @Override
    public Long getMaxCachedUtxoCount() {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = databaseManager.getUnspentTransactionOutputDatabaseManager();
            return unspentTransactionOutputDatabaseManager.getMaxUtxoCount();
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
            return null;
        }
    }

    @Override
    public Long getUncommittedUtxoCount() {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = databaseManager.getUnspentTransactionOutputDatabaseManager();
            return unspentTransactionOutputDatabaseManager.getUncommittedUnspentTransactionOutputCount(true);
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
            return unspentTransactionOutputDatabaseManager.getCommittedUnspentTransactionOutputBlockHeight(true);
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
            return null;
        }
    }

    @Override
    public void commitUtxoCache() {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = databaseManager.getUnspentTransactionOutputDatabaseManager();
            Logger.info("Committing UTXO set.");
            unspentTransactionOutputDatabaseManager.commitUnspentTransactionOutputs(_databaseManagerFactory, CommitAsyncMode.BLOCK_IF_BUSY);
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
        }
    }
}
