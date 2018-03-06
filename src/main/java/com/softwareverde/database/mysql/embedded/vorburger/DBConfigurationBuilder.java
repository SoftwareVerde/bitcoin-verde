package com.softwareverde.database.mysql.embedded.vorburger;

public class DBConfigurationBuilder {
    protected final ch.vorburger.mariadb4j.DBConfigurationBuilder _dbConfigurationBuilder;

    protected DBConfigurationBuilder(final ch.vorburger.mariadb4j.DBConfigurationBuilder dbConfigurationBuilder) {
        _dbConfigurationBuilder = dbConfigurationBuilder;
    }

    public static DBConfigurationBuilder newBuilder() {
        final ch.vorburger.mariadb4j.DBConfigurationBuilder rawDbConfigurationBuilder = ch.vorburger.mariadb4j.DBConfigurationBuilder.newBuilder();
        return new DBConfigurationBuilder(rawDbConfigurationBuilder);
    }

    public void setPort(final int port) {
        _dbConfigurationBuilder.setPort(port);
    }

    public void setDataDir(final String dataDirectory) {
        _dbConfigurationBuilder.setDataDir(dataDirectory);
    }

    public void setSecurityDisabled(final boolean securityIsDisabled) {
        _dbConfigurationBuilder.setSecurityDisabled(securityIsDisabled);
    }

    public DBConfiguration build() {
        return new DBConfiguration(_dbConfigurationBuilder.build());
    }

    public String getURL(final String databaseName) {
        return _dbConfigurationBuilder.getURL(databaseName);
    }
}