package com.softwareverde.bitcoin.server;

import com.softwareverde.database.mysql.embedded.properties.DatabaseProperties;
import com.softwareverde.json.Json;
import com.softwareverde.util.Util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class Configuration {
    public static final Integer BITCOIN_PORT = 8333;
    public static final Integer BITCOIN_RPC_PORT = 8334;
    public static final Integer STRATUM_PORT = 3333;

    public static class SeedNodeProperties {
        private final String _address;
        private final Integer _port;

        public SeedNodeProperties(final String address, final Integer port) {
            _address = address;
            _port = port;
        }

        public String getAddress() { return _address; }
        public Integer getPort() { return _port; }
    }

    public static class ServerProperties {
        private Integer _bitcoinPort;
        private Integer _stratumPort;
        private Integer _bitcoinRpcPort;
        private SeedNodeProperties[] _seedNodeProperties;
        private Integer _maxPeerCount;
        private Integer _maxBlockQueueSize;
        private Integer _trustedBlockHeight;

        public Integer getBitcoinPort() { return _bitcoinPort; }
        public Integer getBitcoinRpcPort() { return _bitcoinRpcPort; }
        public Integer getStratumPort() { return _stratumPort; }
        public SeedNodeProperties[] getSeedNodeProperties() { return Util.copyArray(_seedNodeProperties); }
        public Integer getMaxPeerCount() { return _maxPeerCount; }
        public Integer getMaxBlockQueueSize() { return _maxBlockQueueSize; }
        public Integer getTrustedBlockHeight() { return _trustedBlockHeight; }
    }

    public static class ExplorerProperties {
        private Integer _port;
        private String _rootDirectory;
        private String _bitcoinRpcUrl;
        private Integer _bitcoinRpcPort;

        public Integer getPort() { return _port; }
        public String getRootDirectory() { return _rootDirectory; }
        public String getBitcoinRpcUrl() { return _bitcoinRpcUrl; }
        public Integer getBitcoinRpcPort() { return _bitcoinRpcPort; }
    }

    private final Properties _properties;
    private DatabaseProperties _databaseProperties;
    private ServerProperties _serverProperties;
    private ExplorerProperties _explorerProperties;

    private void _loadDatabaseProperties() {
        final String rootPassword = _properties.getProperty("database.rootPassword", "d3d4a3d0533e3e83bc16db93414afd96");
        final String connectionUrl = _properties.getProperty("database.url", "");
        final String username = _properties.getProperty("database.username", "root");
        final String password = _properties.getProperty("database.password", "");
        final String schema = (_properties.getProperty("database.schema", "bitcoin")).replaceAll("[^A-Za-z0-9_]", "");
        final Integer port = Util.parseInt(_properties.getProperty("database.port", "8336"));
        final String dataDirectory = _properties.getProperty("database.dataDirectory", "data");

        final DatabaseProperties databaseProperties = new DatabaseProperties();
        databaseProperties.setRootPassword(rootPassword);
        databaseProperties.setConnectionUrl(connectionUrl);
        databaseProperties.setUsername(username);
        databaseProperties.setPassword(password);
        databaseProperties.setSchema(schema);
        databaseProperties.setPort(port);
        databaseProperties.setDataDirectory(dataDirectory);
        _databaseProperties = databaseProperties;
    }

    private void _loadServerProperties() {
        _serverProperties = new ServerProperties();
        _serverProperties._bitcoinPort = Util.parseInt(_properties.getProperty("bitcoin.port", BITCOIN_PORT.toString()));
        _serverProperties._bitcoinRpcPort = Util.parseInt(_properties.getProperty("bitcoin.rpcPort", BITCOIN_RPC_PORT.toString()));
        _serverProperties._stratumPort = Util.parseInt(_properties.getProperty("stratum.port", STRATUM_PORT.toString()));

        final Json seedNodesJson = Json.parse(_properties.getProperty("bitcoin.seedNodes", "[\"btc.softwareverde.com\"]"));
        _serverProperties._seedNodeProperties = new SeedNodeProperties[seedNodesJson.length()];
        for (int i = 0; i < seedNodesJson.length(); ++i) {
            final String propertiesString = seedNodesJson.getString(i);

            final SeedNodeProperties seedNodeProperties;
            final int indexOfColon = propertiesString.indexOf(":");
            if (indexOfColon < 0) {
                seedNodeProperties = new SeedNodeProperties(propertiesString, BITCOIN_PORT);
            }
            else {
                final String address = propertiesString.substring(0, indexOfColon);
                final Integer port = Util.parseInt(propertiesString.substring(indexOfColon + 1));
                seedNodeProperties = new SeedNodeProperties(address, port);
            }

            _serverProperties._seedNodeProperties[i] = seedNodeProperties;
        }

        _serverProperties._maxPeerCount = Util.parseInt(_properties.getProperty("bitcoin.maxPeerCount", "24"));
        _serverProperties._maxBlockQueueSize = Util.parseInt(_properties.getProperty("bitcoin.maxBlockQueueSize", "56"));
        _serverProperties._trustedBlockHeight = Util.parseInt(_properties.getProperty("bitcoin.trustedBlockHeight", "0"));
    }

    private void _loadExplorerProperties() {
        final Integer port = Util.parseInt(_properties.getProperty("explorer.port", "8080"));
        final String rootDirectory = _properties.getProperty("explorer.rootDirectory", "www");
        final String bitcoinRpcUrl = _properties.getProperty("explorer.bitcoinRpcUrl", "");
        final Integer bitcoinRpcPort = Util.parseInt(_properties.getProperty("explorer.bitcoinRpcPort", BITCOIN_RPC_PORT.toString()));

        final ExplorerProperties explorerProperties = new ExplorerProperties();
        explorerProperties._port = port;
        explorerProperties._rootDirectory = rootDirectory;
        explorerProperties._bitcoinRpcUrl = bitcoinRpcUrl;
        explorerProperties._bitcoinRpcPort = bitcoinRpcPort;

        _explorerProperties = explorerProperties;
    }

    public Configuration(final File configurationFile) {
        _properties = new Properties();

        try {
            _properties.load(new FileInputStream(configurationFile));
        } catch (final IOException e) { }

        _loadDatabaseProperties();

        _loadServerProperties();

        _loadExplorerProperties();
    }

    public DatabaseProperties getDatabaseProperties() { return _databaseProperties; }
    public ServerProperties getServerProperties() { return _serverProperties; }
    public ExplorerProperties getExplorerProperties() { return _explorerProperties; }
}
