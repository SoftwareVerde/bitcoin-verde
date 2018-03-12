package com.softwareverde.bitcoin.server;

import com.softwareverde.database.mysql.embedded.EmbeddedMysqlDatabase;

public class Environment {
    protected final EmbeddedMysqlDatabase _database;

    public Environment(final EmbeddedMysqlDatabase database) {
        _database = database;
    }

    public EmbeddedMysqlDatabase getDatabase() {
        return _database;
    }
}