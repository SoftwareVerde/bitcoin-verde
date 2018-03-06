package com.softwareverde.database.mysql.embedded.vorburger;

public class DBConfiguration {
    protected final ch.vorburger.mariadb4j.DBConfiguration _dbConfiguration;

    public DBConfiguration(final ch.vorburger.mariadb4j.DBConfiguration dbConfiguration) {
        _dbConfiguration = dbConfiguration;
    }

    public ch.vorburger.mariadb4j.DBConfiguration getRawDbConfiguration() {
        return _dbConfiguration;
    }
}
