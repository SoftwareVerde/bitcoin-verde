package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;

public class SpentTransactionOutputsCleanupService extends SleepyService {
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;

    @Override
    protected void _onStart() { }

    @Override
    protected Boolean _run() {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
            while (true) {
                databaseConnection.executeSql(new Query("DELETE stale_committed_unspent_transaction_outputs, committed_unspent_transaction_outputs FROM committed_unspent_transaction_outputs LEFT OUTER JOIN stale_committed_unspent_transaction_outputs ON (committed_unspent_transaction_outputs.transaction_hash = stale_committed_unspent_transaction_outputs.transaction_hash AND committed_unspent_transaction_outputs.`index` = stale_committed_unspent_transaction_outputs.`index`) WHERE committed_unspent_transaction_outputs.is_spent = 1 LIMIT 1024"));
                final Integer rowsAffected = databaseConnection.getRowsAffectedCount();
                if (rowsAffected < 1) {
                    return false;
                }
            }
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
            return false;
        }
    }

    @Override
    protected void _onSleep() { }

    public SpentTransactionOutputsCleanupService(final FullNodeDatabaseManagerFactory databaseManagerFactory) {
        _databaseManagerFactory = databaseManagerFactory;
    }
}
