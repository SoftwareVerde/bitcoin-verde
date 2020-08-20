package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.database.query.ValueExtractor;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.database.query.parameter.InClauseParameter;
import com.softwareverde.database.row.Row;
import com.softwareverde.logging.Logger;
import com.softwareverde.security.hash.sha256.Sha256Hash;

public class SpentTransactionOutputsCleanupService extends SleepyService {
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;

    @Override
    protected void _onStart() {
        final Thread thread = Thread.currentThread();
        thread.setPriority(Thread.MIN_PRIORITY + 2);
    }

    @Override
    protected Boolean _run() {
        final Thread currentThread = Thread.currentThread();
        // final ReentrantReadWriteLock.ReadLock UTXO_READ_MUTEX = UnspentTransactionOutputDatabaseManager.UTXO_READ_MUTEX; // Use the READ lock since modification is only occurring on stale data...

        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
            while (! currentThread.isInterrupted()) {
                // UTXO_READ_MUTEX.lock(); // Re-acquire the lock each time in order to prevent blocking synchronization...
                try {
                    final java.util.List<Row> rows = databaseConnection.query(new Query("SELECT * FROM stale_committed_unspent_transaction_outputs LIMIT 1024"));
                    if (rows.isEmpty()) { break; }

                    databaseConnection.executeSql(
                        new Query(
                            "DELETE " +
                                "stale_outputs_queue, committed_outputs " +
                            "FROM " +
                                "stale_committed_unspent_transaction_outputs AS stale_outputs_queue LEFT OUTER JOIN committed_unspent_transaction_outputs AS committed_outputs " +
                                    "ON (stale_outputs_queue.transaction_hash = committed_outputs.transaction_hash AND stale_outputs_queue.`index` = committed_outputs.`index` AND committed_outputs.is_spent = 1) " +
                            "WHERE " +
                                "(stale_outputs_queue.transaction_hash, stale_outputs_queue.`index`) IN (?)"
                        )
                            .setExpandedInClauseParameters(rows, new ValueExtractor<Row>() {
                                @Override
                                public InClauseParameter extractValues(final Row row) {
                                    final Sha256Hash transactionHash = Sha256Hash.wrap(row.getBytes("transaction_hash"));
                                    final Integer outputIndex = row.getInteger("index");

                                    final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, outputIndex);
                                    return ValueExtractor.TRANSACTION_OUTPUT_IDENTIFIER.extractValues(transactionOutputIdentifier);
                                }
                            })
                    );
                }
                finally {
                    // UTXO_READ_MUTEX.unlock();
                }

                Thread.sleep(100L); // Allow allow threads to acquire UTXO lock.
            }
        }
        catch (final Exception exception) {
            Logger.debug(exception);
        }

        return false;
    }

    @Override
    protected void _onSleep() { }

    public SpentTransactionOutputsCleanupService(final FullNodeDatabaseManagerFactory databaseManagerFactory) {
        _databaseManagerFactory = databaseManagerFactory;
    }
}
