package com.softwareverde.bitcoin.test;

import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.cache.DisabledDatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.cache.utxo.NativeUnspentTransactionOutputCache;
import com.softwareverde.concurrent.pool.MainThreadPool;
import com.softwareverde.database.mysql.DatabaseInitializer;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.io.Logger;
import com.softwareverde.test.database.MysqlTestDatabase;
import com.softwareverde.test.database.TestDatabase;

public class IntegrationTest {
    protected static final TestDatabase _database = new TestDatabase(new MysqlTestDatabase());
    protected static final Boolean _nativeCacheIsEnabled = NativeUnspentTransactionOutputCache.isEnabled();
    protected static Boolean _nativeCacheWasInitialized = false;

    protected DatabaseManagerCache _databaseManagerCache = new DisabledDatabaseManagerCache();
    protected MainThreadPool _threadPool = new MainThreadPool(1, 1L);

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
