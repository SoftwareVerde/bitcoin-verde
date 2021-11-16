package com.softwareverde.bitcoin.server.configuration;

import com.softwareverde.database.properties.MutableDatabaseProperties;

import java.io.File;

public class BitcoinVerdeDatabaseProperties extends MutableDatabaseProperties {
    protected Boolean _shouldUseEmbeddedDatabase;
    protected Long _maxMemoryByteCount;
    protected Long _logFileByteCount;
    protected File _dataDirectory;
    protected File _installationDirectory;

    public BitcoinVerdeDatabaseProperties() { }

    public BitcoinVerdeDatabaseProperties(final BitcoinVerdeDatabaseProperties databaseProperties) {
        super(databaseProperties);
        _shouldUseEmbeddedDatabase = databaseProperties.shouldUseEmbeddedDatabase();
        _maxMemoryByteCount = databaseProperties.getMaxMemoryByteCount();
        _logFileByteCount = databaseProperties.getLogFileByteCount();
        _dataDirectory = databaseProperties.getDataDirectory();
        _installationDirectory = databaseProperties.getInstallationDirectory();
    }

    public Boolean shouldUseEmbeddedDatabase() { return _shouldUseEmbeddedDatabase; }
    public Long getMaxMemoryByteCount() { return _maxMemoryByteCount; }
    public Long getLogFileByteCount() { return _logFileByteCount; }
    public File getDataDirectory() { return _dataDirectory; }
    public File getInstallationDirectory() { return _installationDirectory; }

    public void setShouldUseEmbeddedDatabase(final Boolean shouldUseEmbeddedDatabase) {
        _shouldUseEmbeddedDatabase = shouldUseEmbeddedDatabase;
    }

    public void setMaxMemoryByteCount(final Long maxMemoryByteCount) {
        _maxMemoryByteCount = maxMemoryByteCount;
    }

    public void setLogFileByteCount(final Long logFileByteCount) {
        _logFileByteCount = logFileByteCount;
    }

    public void setDataDirectory(final File dataDirectory) {
        _dataDirectory = dataDirectory;
    }

    public void setInstallationDirectory(final File installationDirectory) {
        _installationDirectory = installationDirectory;
    }
}