package com.softwareverde.bitcoin.server.module.node.rpc.handler;

import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.indexer.BlockchainIndexerDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.slp.SlpTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.rpc.NodeRpcHandler;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;

public class RpcIndexerHandler implements NodeRpcHandler.IndexerHandler {
    final FullNodeDatabaseManagerFactory _databaseManagerFactory;

    public RpcIndexerHandler(final FullNodeDatabaseManagerFactory databaseManagerFactory) {
        _databaseManagerFactory = databaseManagerFactory;
    }

    @Override
    public Boolean clearTransactionIndexes() {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            _clearTransactionIndexes(databaseManager);
            return true;
        }
        catch (final Exception exception) {
            Logger.error("Unable to clear transaction indexes", exception);
            return false;
        }
    }

    protected void _clearTransactionIndexes(final FullNodeDatabaseManager databaseManager) throws DatabaseException {
        Logger.debug("Clearing transaction index.");
        final BlockchainIndexerDatabaseManager blockchainIndexerDatabaseManager = databaseManager.getBlockchainIndexerDatabaseManager();
        blockchainIndexerDatabaseManager.deleteTransactionIndexes();
    }

    @Override
    public Boolean clearAllSlpValidation() {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            _clearSlpValidationCache(databaseManager);
            return true;
        }
        catch (final Exception exception) {
            Logger.error("Unable to clear SLP validation", exception);
            return false;
        }
    }

    protected void _clearSlpValidationCache(final FullNodeDatabaseManager databaseManager) throws DatabaseException {
        Logger.debug("Clearing cached SLP validation results.");
        final SlpTransactionDatabaseManager slpTransactionDatabaseManager = databaseManager.getSlpTransactionDatabaseManager();
        slpTransactionDatabaseManager.deleteAllSlpValidationResults();
    }
}
