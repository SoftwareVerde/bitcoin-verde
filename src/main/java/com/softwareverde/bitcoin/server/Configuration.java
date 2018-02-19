package com.softwareverde.bitcoin.server;

import com.softwareverde.util.Util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class Configuration {
    public static class DatabaseProperties {
        private String _connectionUrl;
        private String _username;
        private String _password;
        private String _schema;
        private Integer _port;
        private String _dataDirectory;
        private String _tmpDirectory;

        public String getConnectionUrl() { return _connectionUrl; }
        public String getUsername() { return _username; }
        public String getPassword() { return _password; }
        public String getSchema() { return _schema; }
        public Integer getPort() { return _port; }
        public String getDataDirectory() { return _dataDirectory; }
        public String getTmpDirectory() { return _tmpDirectory; }
    }

    public static class ServerProperties {
        private Integer _bitcoinPort;

        public Integer getBitcoinPort() { return _bitcoinPort; }
    }

    private final Properties _properties;
    private DatabaseProperties _databaseProperties;
    private ServerProperties _serverProperties;

    private void _loadDatabaseProperties() {
        _databaseProperties = new DatabaseProperties();
        _databaseProperties._connectionUrl = _properties.getProperty("database.url", "");
        _databaseProperties._username = _properties.getProperty("database.username", "");
        _databaseProperties._password = _properties.getProperty("database.password", "");
        _databaseProperties._schema = _properties.getProperty("database.schema", "");
        _databaseProperties._port = Util.parseInt(_properties.getProperty("database.port", "8336"));
        _databaseProperties._dataDirectory = _properties.getProperty("database.dataDirectory", "data");
        _databaseProperties._tmpDirectory = _properties.getProperty("database.tmpDirectory", "tmp");
    }

    private void _loadServerProperties() {
        _serverProperties = new ServerProperties();
        _serverProperties._bitcoinPort = Util.parseInt(_properties.getProperty("bitcoin.port", "8333"));
    }

    public Configuration(final File configurationFile) {
        _properties = new Properties();

        try {
            _properties.load(new FileInputStream(configurationFile));
        } catch (final IOException e) { }

        _loadDatabaseProperties();

        _loadServerProperties();

    }

    public DatabaseProperties getDatabaseProperties() { return _databaseProperties; }
    public ServerProperties getServerProperties() { return _serverProperties; }
}
