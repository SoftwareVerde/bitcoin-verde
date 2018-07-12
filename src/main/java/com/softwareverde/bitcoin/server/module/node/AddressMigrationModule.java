package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.database.TransactionInputDatabaseManager;
import com.softwareverde.bitcoin.server.database.TransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.transaction.input.TransactionInputId;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.transaction.script.locking.ImmutableLockingScript;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.unlocking.ImmutableUnlockingScript;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.database.mysql.embedded.properties.DatabaseProperties;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.io.Logger;
import com.softwareverde.util.Util;

import java.io.File;

public class AddressMigrationModule {
    public static void execute(final String configurationFileName) {
        final AddressMigrationModule addressMigrationModule = new AddressMigrationModule(configurationFileName);
        addressMigrationModule.run();
    }

    protected final Configuration _configuration;

    protected Configuration _loadConfigurationFile(final String configurationFilename) {
        final File configurationFile =  new File(configurationFilename);
        if (! configurationFile.isFile()) {
            Logger.log("Invalid configuration file.");
            BitcoinUtil.exitFailure();
        }

        return new Configuration(configurationFile);
    }

    protected AddressMigrationModule(final String configurationFilename) {
        _configuration = _loadConfigurationFile(configurationFilename);
    }

    public void run() {
        final MysqlDatabaseConnectionFactory databaseConnectionFactory;
        {
            final DatabaseProperties databaseProperties = _configuration.getDatabaseProperties();
            final String connectionUrl = MysqlDatabaseConnectionFactory.createConnectionString(databaseProperties.getConnectionUrl(), databaseProperties.getPort(), databaseProperties.getSchema());
            final String username = databaseProperties.getUsername();
            final String password = databaseProperties.getPassword();

            databaseConnectionFactory = new MysqlDatabaseConnectionFactory(connectionUrl, username, password);
        }

        final Thread transactionInputMigrationThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try (final MysqlDatabaseConnection databaseConnection = databaseConnectionFactory.newConnection()) {
                    long nextId = 1L;
                    {
                        final java.util.List<Row> rows = databaseConnection.query(
                            new Query("SELECT id, transaction_input_id FROM unlocking_scripts ORDER BY id DESC LIMIT 1")
                        );
                        if (! rows.isEmpty()) {
                            final Row row = rows.get(0);
                            nextId = row.getLong("transaction_input_id") + 1;
                        }
                    }

                    Logger.log("Starting input migration at: " + nextId);

                    final TransactionInputDatabaseManager transactionInputDatabaseManager = new TransactionInputDatabaseManager(databaseConnection);

                    long maxId = Long.MAX_VALUE;

                    while (true) {
                        if (nextId % 10000 == 0) {
                            final java.util.List<Row> rows = databaseConnection.query(
                                new Query("SELECT id FROM transaction_inputs ORDER BY id DESC LIMIT 1")
                            );

                            maxId = (rows.get(0).getLong("id"));

                            Logger.log("Input Migration: " + nextId + " / " + maxId + " ("+ (nextId * 100F / (float) maxId) +"%)");
                        }

                        final java.util.List<Row> rows = databaseConnection.query(
                            new Query("SELECT id, unlocking_script FROM transaction_inputs WHERE id = ?")
                                .setParameter(nextId)
                        );
                        if (rows.isEmpty()) {
                            Logger.log("Skipping Input Migration Id: " + nextId);
                            if (nextId >= maxId) { Thread.sleep(500L); }
                            nextId += 1L;
                            continue;
                        }

                        final Row row = rows.get(0);
                        final TransactionInputId transactionInputId = TransactionInputId.wrap(row.getLong("id"));
                        final UnlockingScript unlockingScript = new ImmutableUnlockingScript(row.getBytes("unlocking_script"));

                        TransactionUtil.startTransaction(databaseConnection);
                        transactionInputDatabaseManager._insertUnlockingScript(transactionInputId, unlockingScript);
                        TransactionUtil.commitTransaction(databaseConnection);

                        nextId += 1L;
                    }
                }
                catch (final Exception exception) {
                    Logger.log(exception);
                }

                Logger.log("***** Database Migration Ended *****");
            }
        });

        final Thread transactionOutputMigrationThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try (final MysqlDatabaseConnection databaseConnection = databaseConnectionFactory.newConnection()) {
                    long nextId = 1L;
                    {
                        final java.util.List<Row> rows = databaseConnection.query(
                            new Query("SELECT id, transaction_output_id FROM locking_scripts ORDER BY id DESC LIMIT 1")
                        );
                        if (! rows.isEmpty()) {
                            final Row row = rows.get(0);
                            nextId = row.getLong("transaction_output_id") + 1;
                        }
                    }

                    Logger.log("Starting output migration at: " + nextId);

                    final TransactionOutputDatabaseManager transactionOutputDatabaseManager = new TransactionOutputDatabaseManager(databaseConnection);

                    long maxId = Long.MAX_VALUE;
                    while (true) {
                        if (nextId % 10000 == 0) {
                            final java.util.List<Row> rows = databaseConnection.query(
                                new Query("SELECT id FROM transaction_outputs ORDER BY id DESC LIMIT 1")
                            );

                            maxId = (rows.get(0).getLong("id"));

                            Logger.log("Output Migration: " + nextId + " / " + maxId + " ("+ (nextId * 100F / (float) maxId) +"%)");
                        }

                        final java.util.List<Row> rows = databaseConnection.query(
                            new Query("SELECT id, locking_script FROM transaction_outputs WHERE id = ?")
                                .setParameter(nextId)
                        );
                        if (rows.isEmpty()) {
                            Logger.log("Skipping Output Migration Id: " + nextId);
                            if (nextId >= maxId) { Thread.sleep(500L); }
                            nextId += 1L;
                            continue;
                        }

                        final Row row = rows.get(0);
                        final TransactionOutputId transactionOutputId = TransactionOutputId.wrap(row.getLong("id"));
                        final LockingScript lockingScript = new ImmutableLockingScript(row.getBytes("locking_script"));

                        TransactionUtil.startTransaction(databaseConnection);
                        transactionOutputDatabaseManager._storeScriptAddress(lockingScript);
                        transactionOutputDatabaseManager._insertLockingScript(transactionOutputId, lockingScript);
                        TransactionUtil.commitTransaction(databaseConnection);

                        nextId += 1L;
                    }
                }
                catch (final Exception exception) {
                    Logger.log(exception);
                }

                Logger.log("***** Output Database Migration Ended *****");
            }
        });

        Logger.log("Transaction Input Migration Thread: Started");
        transactionInputMigrationThread.start();

        Logger.log("Transaction Output Migration Thread: Started");
        transactionOutputMigrationThread.start();

        try {
            transactionInputMigrationThread.join();
            transactionOutputMigrationThread.join();
        }
        catch (final InterruptedException exception) { }

        Logger.log("Complete.");

        Logger.shutdown();
    }
}
