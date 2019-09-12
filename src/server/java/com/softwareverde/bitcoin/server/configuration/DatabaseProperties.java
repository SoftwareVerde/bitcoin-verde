package com.softwareverde.bitcoin.server.configuration;

import com.softwareverde.database.mysql.embedded.properties.MutableEmbeddedDatabaseProperties;

public class DatabaseProperties extends MutableEmbeddedDatabaseProperties {
    protected Boolean _useEmbeddedDatabase;
    protected Long _maxMemoryByteCount;
    protected Long _logFileByteCount;

    public DatabaseProperties() { }

    public Boolean useEmbeddedDatabase() { return _useEmbeddedDatabase; }
    public Long getMaxMemoryByteCount() { return _maxMemoryByteCount; }
    public Long getLogFileByteCount() { return _logFileByteCount; }
}