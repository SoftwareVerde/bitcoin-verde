package com.softwareverde.bitcoin.test;

import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.cache.DisabledDatabaseManagerCache;
import com.softwareverde.bitcoin.server.main.NativeUnspentTransactionOutputCache;
import com.softwareverde.bitcoin.server.module.node.database.core.CoreDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.spv.SpvDatabaseManagerFactory;
import com.softwareverde.concurrent.pool.MainThreadPool;
import com.softwareverde.database.mysql.DatabaseInitializer;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.database.mysql.connection.ReadUncommittedDatabaseConnectionFactory;
import com.softwareverde.io.Logger;
import com.softwareverde.test.database.MysqlTestDatabase;
import com.softwareverde.test.database.TestDatabase;

public class IntegrationTest {
    protected static final TestDatabase _database = new TestDatabase(new MysqlTestDatabase());
    protected static final Boolean _nativeCacheIsEnabled = NativeUnspentTransactionOutputCache.isEnabled();
    protected static Boolean _nativeCacheWasInitialized = false;

    protected final DatabaseManagerCache _databaseManagerCache = new DisabledDatabaseManagerCache();
    protected final MainThreadPool _threadPool = new MainThreadPool(1, 1L);

    protected final CoreDatabaseManagerFactory _coreDatabaseManagerFactory;
    protected final CoreDatabaseManagerFactory _readUncomittedDatabaseManagerFactory;
    protected final SpvDatabaseManagerFactory _spvDatabaseManagerFactory;

    public IntegrationTest() {
        final DatabaseConnectionFactory databaseConnectionFactory = _database.getDatabaseConnectionFactory();
        _coreDatabaseManagerFactory = new CoreDatabaseManagerFactory(databaseConnectionFactory, _databaseManagerCache);
        _spvDatabaseManagerFactory = new SpvDatabaseManagerFactory(databaseConnectionFactory, _databaseManagerCache);

        final ReadUncommittedDatabaseConnectionFactory readUncommittedDatabaseConnectionFactory = new ReadUncommittedDatabaseConnectionFactory(databaseConnectionFactory);
        _readUncomittedDatabaseManagerFactory = new CoreDatabaseManagerFactory(readUncommittedDatabaseConnectionFactory, _databaseManagerCache);
    }

    static {
        _resetDatabase();
    }

    protected static void _resetDatabase() {
        final DatabaseInitializer databaseInitializer = new DatabaseInitializer("queries/bitcoin_init.sql", 1, new DatabaseInitializer.DatabaseUpgradeHandler() {
            @Override
            public Boolean onUpgrade(final int i, final int i1) { return false; }
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
                Logger.log("NOTICE: NativeUtxoCache not enabled.");
            }
        }
        catch (final Exception exception) {
            throw new RuntimeException(exception);
        }
    }
}
