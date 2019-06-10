package com.softwareverde.bitcoin.server.configuration;

import com.softwareverde.database.mysql.embedded.properties.MutableEmbeddedDatabaseProperties;

public class DatabaseProperties extends MutableEmbeddedDatabaseProperties {
    protected Long _maxMemoryByteCount;
    protected Boolean _useEmbeddedDatabase;

    public DatabaseProperties() { }

    public Long getMaxMemoryByteCount() { return _maxMemoryByteCount; }
    public Boolean useEmbeddedDatabase() { return _useEmbeddedDatabase; }
}