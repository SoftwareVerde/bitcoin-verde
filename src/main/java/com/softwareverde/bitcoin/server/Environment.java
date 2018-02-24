package com.softwareverde.bitcoin.server;

import ch.vorburger.mariadb4j.DB;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.util.Container;

public class Environment {
    private static final Container<DB> _mysqlInstance = new Container<DB>();

    protected final BitcoinDatabase _database;

    public Environment(final DB database, final DatabaseConnectionFactory databaseConnectionFactory) {
        _mysqlInstance.value = database;
        _database = new BitcoinDatabase(databaseConnectionFactory);
    }

    public BitcoinDatabase getDatabase() {
        return _database;
    }
}