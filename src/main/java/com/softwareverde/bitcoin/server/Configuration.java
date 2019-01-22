package com.softwareverde.bitcoin.server;

import com.softwareverde.database.mysql.embedded.properties.MutableEmbeddedDatabaseProperties;
import com.softwareverde.io.Logger;
import com.softwareverde.json.Json;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.Util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class Configuration {
    public static final Integer BITCOIN_PORT = 8333;
    public static final Integer BITCOIN_RPC_PORT = 8334;
    public static final Integer STRATUM_PORT = 3333;

    public static final Integer EXPLORER_PORT = 8080;
    public static final Integer EXPLORER_TLS_PORT = 4443;

    public static final Integer WALLET_PORT = 8081;
    public static final Integer WALLET_TLS_PORT = 4444;

    public static class DatabaseProperties extends MutableEmbeddedDatabaseProperties {
        protected Long _maxMemoryByteCount;
        protected Boolean _useEmbeddedDatabase;

        public DatabaseProperties() { }

        public Long getMaxMemoryByteCount() { return _maxMemoryByteCount; }
        public Boolean useEmbeddedDatabase() { return _useEmbeddedDatabase; }
    }

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
        private Integer _maxThreadCount;
        private Long _trustedBlockHeight;
        private Boolean _shouldSkipNetworking;
        private Long _maxUtxoCacheByteCount;
        private Boolean _useTransactionBloomFilter;
        private Boolean _shouldTrimBlocks;
        private Integer _maxMessagesPerSecond;

        public Integer getBitcoinPort() { return _bitcoinPort; }
        public Integer getBitcoinRpcPort() { return _bitcoinRpcPort; }
        public Integer getStratumPort() { return _stratumPort; }
        public SeedNodeProperties[] getSeedNodeProperties() { return Util.copyArray(_seedNodeProperties); }
        public Integer getMaxPeerCount() { return _maxPeerCount; }
        public Integer getMaxThreadCount() { return _maxThreadCount; }
        public Long getTrustedBlockHeight() { return _trustedBlockHeight; }
        public Boolean skipNetworking() { return _shouldSkipNetworking; }
        public Long getMaxUtxoCacheByteCount() { return _maxUtxoCacheByteCount; }
        public Boolean shouldUseTransactionBloomFilter() { return _useTransactionBloomFilter; }
        public Boolean shouldTrimBlocks() { return _shouldTrimBlocks; }
        public Integer getMaxMessagesPerSecond() { return _maxMessagesPerSecond; }
    }

    public static class ExplorerProperties {
        private Integer _port;
        private String _rootDirectory;
        private String _bitcoinRpcUrl;
        private Integer _bitcoinRpcPort;

        private Integer _tlsPort;
        private String _tlsKeyFile;
        private String _tlsCertificateFile;

        public Integer getPort() { return _port; }
        public String getRootDirectory() { return _rootDirectory; }
        public String getBitcoinRpcUrl() { return _bitcoinRpcUrl; }
        public Integer getBitcoinRpcPort() { return _bitcoinRpcPort; }

        public Integer getTlsPort() { return _tlsPort; }
        public String getTlsKeyFile() { return _tlsKeyFile; }
        public String getTlsCertificateFile() { return _tlsCertificateFile; }
    }

    public static class WalletProperties {
        private Integer _port;
        private String _rootDirectory;
        private String _explorerUrl;
        private Integer _explorerPort;
        private Integer _explorerTlsPort;

        private Integer _tlsPort;
        private String _tlsKeyFile;
        private String _tlsCertificateFile;

        public Integer getPort() { return _port; }
        public String getRootDirectory() { return _rootDirectory; }
        public String getExplorerUrl() { return _explorerUrl; }
        public Integer getExplorerPort() { return _explorerPort; }
        public Integer getExplorerTlsPort() { return _explorerTlsPort; }

        public Integer getTlsPort() { return _tlsPort; }
        public String getTlsKeyFile() { return _tlsKeyFile; }
        public String getTlsCertificateFile() { return _tlsCertificateFile; }
    }

    private final Properties _properties;
    private DatabaseProperties _databaseProperties;
    private ServerProperties _serverProperties;
    private ExplorerProperties _explorerProperties;
    private WalletProperties _walletProperties;

    private void _loadDatabaseProperties() {
        final String rootPassword = _properties.getProperty("database.rootPassword", "d3d4a3d0533e3e83bc16db93414afd96");
        final String hostname = _properties.getProperty("database.hostname", "");
        final String username = _properties.getProperty("database.username", "root");
        final String password = _properties.getProperty("database.password", "");
        final String schema = (_properties.getProperty("database.schema", "bitcoin")).replaceAll("[^A-Za-z0-9_]", "");
        final Integer port = Util.parseInt(_properties.getProperty("database.port", "8336"));
        final String dataDirectory = _properties.getProperty("database.dataDirectory", "data");
        final Long maxMemoryByteCount = Util.parseLong(_properties.getProperty("database.maxMemoryByteCount", String.valueOf(2L * ByteUtil.Unit.GIGABYTES)));
        final Boolean useEmbeddedDatabase = Util.parseBool(_properties.getProperty("database.useEmbeddedDatabase", "1"));

        final DatabaseProperties databaseProperties = new DatabaseProperties();
        databaseProperties.setRootPassword(rootPassword);
        databaseProperties.setHostname(hostname);
        databaseProperties.setUsername(username);
        databaseProperties.setPassword(password);
        databaseProperties.setSchema(schema);
        databaseProperties.setPort(port);
        databaseProperties.setDataDirectory(dataDirectory);
        databaseProperties._maxMemoryByteCount = maxMemoryByteCount;
        databaseProperties._useEmbeddedDatabase = useEmbeddedDatabase;
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
        _serverProperties._maxThreadCount = Util.parseInt(_properties.getProperty("bitcoin.maxThreadCount", "4"));
        _serverProperties._trustedBlockHeight = Util.parseLong(_properties.getProperty("bitcoin.trustedBlockHeight", "0"));
        _serverProperties._shouldSkipNetworking = Util.parseBool(_properties.getProperty("bitcoin.skipNetworking", "0"));
        _serverProperties._maxUtxoCacheByteCount = Util.parseLong(_properties.getProperty("bitcoin.maxUtxoCacheByteCount", String.valueOf(512L * ByteUtil.Unit.MEGABYTES)));
        _serverProperties._useTransactionBloomFilter = Util.parseBool(_properties.getProperty("bitcoin.useTransactionBloomFilter", "1"));
        _serverProperties._shouldTrimBlocks = Util.parseBool(_properties.getProperty("bitcoin.trimBlocks", "0"));
        _serverProperties._maxMessagesPerSecond = Util.parseInt(_properties.getProperty("bitcoin.maxMessagesPerSecondPerNode", "250"));
    }

    private void _loadExplorerProperties() {
        final Integer port = Util.parseInt(_properties.getProperty("explorer.port", EXPLORER_PORT.toString()));
        final String rootDirectory = _properties.getProperty("explorer.rootDirectory", "explorer/www");
        final String bitcoinRpcUrl = _properties.getProperty("explorer.bitcoinRpcUrl", "");
        final Integer bitcoinRpcPort = Util.parseInt(_properties.getProperty("explorer.bitcoinRpcPort", BITCOIN_RPC_PORT.toString()));

        final Integer tlsPort = Util.parseInt(_properties.getProperty("explorer.tlsPort", EXPLORER_TLS_PORT.toString()));
        final String tlsKeyFile = _properties.getProperty("explorer.tlsKeyFile", "");
        final String tlsCertificateFile = _properties.getProperty("explorer.tlsCertificateFile", "");

        final ExplorerProperties explorerProperties = new ExplorerProperties();
        explorerProperties._port = port;
        explorerProperties._rootDirectory = rootDirectory;
        explorerProperties._bitcoinRpcUrl = bitcoinRpcUrl;
        explorerProperties._bitcoinRpcPort = bitcoinRpcPort;

        explorerProperties._tlsPort = tlsPort;
        explorerProperties._tlsKeyFile = (tlsKeyFile.isEmpty() ? null : tlsKeyFile);
        explorerProperties._tlsCertificateFile = (tlsCertificateFile.isEmpty() ? null : tlsCertificateFile);

        _explorerProperties = explorerProperties;
    }

    private void _loadWalletProperties() {
        final Integer port = Util.parseInt(_properties.getProperty("wallet.port", WALLET_PORT.toString()));
        final String rootDirectory = _properties.getProperty("wallet.rootDirectory", "wallet/www");
        final String explorerUrl = _properties.getProperty("wallet.explorerUrl", "");
        final Integer explorerPort = Util.parseInt(_properties.getProperty("wallet.explorerPort", EXPLORER_PORT.toString()));
        final Integer explorerTlsPort = Util.parseInt(_properties.getProperty("wallet.explorerTlsPort", EXPLORER_TLS_PORT.toString()));

        final Integer tlsPort = Util.parseInt(_properties.getProperty("wallet.tlsPort", WALLET_TLS_PORT.toString()));
        final String tlsKeyFile = _properties.getProperty("wallet.tlsKeyFile", "");
        final String tlsCertificateFile = _properties.getProperty("wallet.tlsCertificateFile", "");

        final WalletProperties walletProperties = new WalletProperties();
        walletProperties._port = port;
        walletProperties._rootDirectory = rootDirectory;
        walletProperties._explorerUrl = explorerUrl;
        walletProperties._explorerPort = explorerPort;
        walletProperties._explorerTlsPort = explorerTlsPort;

        walletProperties._tlsPort = tlsPort;
        walletProperties._tlsKeyFile = (tlsKeyFile.isEmpty() ? null : tlsKeyFile);
        walletProperties._tlsCertificateFile = (tlsCertificateFile.isEmpty() ? null : tlsCertificateFile);

        _walletProperties = walletProperties;
    }

    public Configuration(final File configurationFile) {
        _properties = new Properties();

        try (final FileInputStream fileInputStream = new FileInputStream(configurationFile)) {
            _properties.load(fileInputStream);
        }
        catch (final IOException exception) {
            Logger.log("NOTICE: Unable to load properties.");
        }

        _loadDatabaseProperties();

        _loadServerProperties();

        _loadExplorerProperties();

        _loadWalletProperties();
    }

    public DatabaseProperties getDatabaseProperties() { return _databaseProperties; }
    public ServerProperties getServerProperties() { return _serverProperties; }
    public ExplorerProperties getExplorerProperties() { return _explorerProperties; }
    public WalletProperties getWalletProperties() { return _walletProperties; }

}
