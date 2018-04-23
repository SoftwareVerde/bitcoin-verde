package com.softwareverde.bitcoin.test;

import com.softwareverde.database.mysql.embedded.DatabaseInitializer;
import com.softwareverde.test.database.MysqlTestDatabase;

public class IntegrationTest {
    protected static final MysqlTestDatabase _database = new MysqlTestDatabase();
    static {
        _resetDatabase();
    }

    protected static void _resetDatabase() {
        final DatabaseInitializer databaseInitializer = new DatabaseInitializer("queries/init.sql", 1, new DatabaseInitializer.DatabaseUpgradeHandler() {
            @Override
            public Boolean onUpgrade(final int i, final int i1) { return false; }
        });
        try {
            _database.reset();
            databaseInitializer.initializeDatabase(_database.getDatabaseInstance(), _database.getDatabaseConnectionFactory(), _database.getCredentials());
        }
        catch (final Exception exception) {
            throw new RuntimeException(exception);
        }
    }
}
