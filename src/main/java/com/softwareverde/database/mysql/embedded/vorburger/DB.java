package com.softwareverde.database.mysql.embedded.vorburger;

import ch.vorburger.exec.ManagedProcessException;
import com.softwareverde.database.DatabaseException;

public class DB {
    private final ch.vorburger.mariadb4j.DB _db;

    protected DB(final ch.vorburger.mariadb4j.DB db) {
        _db = db;
    }

    public static DB newEmbeddedDB(final DBConfiguration databaseConfiguration) throws DatabaseException {
        try {
            final ch.vorburger.mariadb4j.DB db = ch.vorburger.mariadb4j.DB.newEmbeddedDB(databaseConfiguration.getRawDbConfiguration());
            return new DB(db);
        }
        catch (final ManagedProcessException exception) {
            throw new DatabaseException(exception);
        }
    }

    public void start() throws DatabaseException {
        try {
            _db.start();
        }
        catch (final ManagedProcessException exception) {
            throw new DatabaseException(exception);
        }
    }

    public void source(final String resource, final String username, final String password, final String dbName) throws DatabaseException {
        try {
            _db.source(resource, username, password, dbName);
        }
        catch (final ManagedProcessException exception) {
            throw new DatabaseException(exception);
        }
    }

    public void createDB(final String databaseSchema) throws DatabaseException {
        try {
            _db.createDB(databaseSchema);
        }
        catch (final ManagedProcessException exception) {
            throw new DatabaseException(exception);
        }
    }

    public void run(final String query, final String rootUsername, final String rootPassword) throws DatabaseException {
        try {
            _db.run(query, rootUsername, rootPassword);
        }
        catch (final ManagedProcessException exception) {
            throw new DatabaseException(exception);
        }
    }

    public ch.vorburger.mariadb4j.DB getRawDb() {
        return _db;
    }
}
