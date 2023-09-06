package com.softwareverde.bitcoin.server.module;

import com.softwareverde.bitcoin.CoreInflater;
import com.softwareverde.bitcoin.server.Environment;
import com.softwareverde.bitcoin.server.configuration.TestNetCheckpointConfiguration;
import com.softwareverde.bitcoin.server.database.Database;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.store.PendingBlockStoreCore;
import com.softwareverde.bitcoin.server.module.node.store.UtxoCommitmentStoreCore;
import com.softwareverde.bitcoin.server.properties.DatabasePropertiesStore;
import com.softwareverde.logging.Logger;

import java.io.File;

//public class DatabaseModule {
//    protected final Environment _environment;
//
//    public DatabaseModule(final Environment environment) {
//        _environment = environment;
//    }
//
//    public void loop() {
//        final File dataDirectory = new File("data");
//        final CoreInflater inflater = new CoreInflater();
//        final Database database = _environment.getDatabase();
//        final DatabaseConnectionFactory databaseConnectionFactory = _environment.getDatabaseConnectionFactory();
//        final Boolean useCompression = true;
//        final FullNodeDatabaseManagerFactory databaseManagerFactory = new FullNodeDatabaseManagerFactory(
//            databaseConnectionFactory,
//            database.getMaxQueryBatchSize(),
//            new DatabasePropertiesStore(databaseConnectionFactory),
//            new PendingBlockStoreCore(dataDirectory, inflater, inflater, useCompression),
//            new UtxoCommitmentStoreCore(dataDirectory.toString()),
//            inflater,
//            new TestNetCheckpointConfiguration(),
//            1073741824L,
//            0.5F,
//            null
//        );
//
//        try (final FullNodeDatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
//            // Scratch space...
//        }
//        catch (final Exception exception) {
//            Logger.debug(exception);
//        }
//
//        while (true) {
//            try { Thread.sleep(5000); } catch (final Exception exception) { break; }
//        }
//    }
//}
