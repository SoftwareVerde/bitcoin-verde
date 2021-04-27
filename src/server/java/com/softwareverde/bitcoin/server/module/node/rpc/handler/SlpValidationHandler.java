package com.softwareverde.bitcoin.server.module.node.rpc.handler;

import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.transaction.slp.SlpTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.rpc.NodeRpcHandler;
import com.softwareverde.logging.Logger;

public class SlpValidationHandler implements NodeRpcHandler.SlpValidationHandler {
    final FullNodeDatabaseManagerFactory _databaseManagerFactory;

    public SlpValidationHandler(final FullNodeDatabaseManagerFactory databaseManagerFactory) {
        _databaseManagerFactory = databaseManagerFactory;
    }

    @Override
    public Boolean clearAllSlpValidation() {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final SlpTransactionDatabaseManager slpTransactionDatabaseManager = databaseManager.getSlpTransactionDatabaseManager();
            Logger.debug("Clearing cached SLP validation results.");
            slpTransactionDatabaseManager.deleteAllSlpValidationResults();
            return true;
        }
        catch (final Exception exception) {
            Logger.error("Unable to clear SLP validation", exception);
            return false;
        }
    }
}
