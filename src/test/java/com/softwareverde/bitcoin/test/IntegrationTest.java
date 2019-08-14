package com.softwareverde.bitcoin.test;

import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.cache.DisabledDatabaseManagerCache;
import com.softwareverde.bitcoin.server.main.NativeUnspentTransactionOutputCache;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.spv.SpvDatabaseManagerFactory;
import com.softwareverde.concurrent.pool.MainThreadPool;
import com.softwareverde.database.DatabaseInitializer;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.database.mysql.MysqlDatabaseInitializer;
import com.softwareverde.database.mysql.connection.ReadUncommittedDatabaseConnectionFactory;
import com.softwareverde.logging.Logger;
import com.softwareverde.test.database.MysqlTestDatabase;
import com.softwareverde.test.database.TestDatabase;

import java.sql.Connection;

public class IntegrationTest extends UnitTest {
    protected static final TestDatabase _database = new TestDatabase(new MysqlTestDatabase());
    protected static final Boolean _nativeCacheIsEnabled = NativeUnspentTransactionOutputCache.isEnabled();
    protected static Boolean _nativeCacheWasInitialized = false;

    protected final DatabaseManagerCache _databaseManagerCache = new DisabledDatabaseManagerCache();
    protected final MainThreadPool _threadPool = new MainThreadPool(1, 1L);

    protected final FullNodeDatabaseManagerFactory _fullNodeDatabaseManagerFactory;
    protected final FullNodeDatabaseManagerFactory _readUncomittedDatabaseManagerFactory;
    protected final SpvDatabaseManagerFactory _spvDatabaseManagerFactory;

    public IntegrationTest() {
        final DatabaseConnectionFactory databaseConnectionFactory = _database.getDatabaseConnectionFactory();
        _fullNodeDatabaseManagerFactory = new FullNodeDatabaseManagerFactory(databaseConnectionFactory, _databaseManagerCache);
        _spvDatabaseManagerFactory = new SpvDatabaseManagerFactory(databaseConnectionFactory, _databaseManagerCache);

        final ReadUncommittedDatabaseConnectionFactory readUncommittedDatabaseConnectionFactory = new ReadUncommittedDatabaseConnectionFactory(databaseConnectionFactory);
        _readUncomittedDatabaseManagerFactory = new FullNodeDatabaseManagerFactory(readUncommittedDatabaseConnectionFactory, _databaseManagerCache);
    }

    static {
        _resetDatabase();
    }

    protected static void _resetDatabase() {
        final DatabaseInitializer<Connection> databaseInitializer = new MysqlDatabaseInitializer("queries/bitcoin_init.sql", 1, new DatabaseInitializer.DatabaseUpgradeHandler<Connection>() {
            @Override
            public Boolean onUpgrade(final com.softwareverde.database.DatabaseConnection<Connection> maintenanceConnection, final Integer currentVersion, final Integer requiredVersion) { return false; }
        });
        try {
            _database.reset();

            final MysqlDatabaseConnectionFactory databaseConnectionFactory = _database.getMysqlDatabaseConnectionFactory();
            try (final MysqlDatabaseConnection databaseConnection = databaseConnectionFactory.newConnection()) {
                databaseInitializer.initializeDatabase(databaseConnection);
            }

            if (_nativeCacheIsEnabled) {
                if (_nativeCacheWasInitialized) {
                    NativeUnspentTransactionOutputCache.destroy();
                }
                NativeUnspentTransactionOutputCache.init();
                _nativeCacheWasInitialized = true;
            }
            else {
                Logger.info("NOTICE: NativeUtxoCache not enabled.");
            }
        }
        catch (final Exception exception) {
            throw new RuntimeException(exception);
        }
    }
}
