package com.softwareverde.bitcoin.server;

import com.softwareverde.database.mysql.embedded.properties.MutableEmbeddedDatabaseProperties;
import com.softwareverde.io.Logger;
import com.softwareverde.json.Json;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.StringUtil;
import com.softwareverde.util.Util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class Configuration {
    public static final Integer BITCOIN_PORT = 8333;
    public static final Integer BITCOIN_RPC_PORT = 8334;

    public static final Integer PROXY_HTTP_PORT = 8080;
    public static final Integer PROXY_TLS_PORT = 4480;

    public static final Integer EXPLORER_HTTP_PORT = 8081;
    public static final Integer EXPLORER_TLS_PORT = 4481;

    public static final Integer STRATUM_PORT = 3333;
    public static final Integer STRATUM_RPC_PORT = 3334;
    public static final Integer STRATUM_HTTP_PORT = 8082;
    public static final Integer STRATUM_TLS_PORT = 4482;

    public static final Integer WALLET_PORT = 8888;
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

    public static class BitcoinProperties {
        private Integer _bitcoinPort;
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

        private DatabaseProperties _databaseProperties;

        public Integer getBitcoinPort() { return _bitcoinPort; }
        public Integer getBitcoinRpcPort() { return _bitcoinRpcPort; }
        public SeedNodeProperties[] getSeedNodeProperties() { return Util.copyArray(_seedNodeProperties); }
        public Integer getMaxPeerCount() { return _maxPeerCount; }
        public Integer getMaxThreadCount() { return _maxThreadCount; }
        public Long getTrustedBlockHeight() { return _trustedBlockHeight; }
        public Boolean skipNetworking() { return _shouldSkipNetworking; }
        public Long getMaxUtxoCacheByteCount() { return _maxUtxoCacheByteCount; }
        public Boolean shouldUseTransactionBloomFilter() { return _useTransactionBloomFilter; }
        public Boolean shouldTrimBlocks() { return _shouldTrimBlocks; }
        public Integer getMaxMessagesPerSecond() { return _maxMessagesPerSecond; }

        public DatabaseProperties getDatabaseProperties() { return _databaseProperties; }
    }

    public static class ExplorerProperties {
        private Integer _port;
        private String _rootDirectory;

        private String _bitcoinRpcUrl;
        private Integer _bitcoinRpcPort;

        private String _stratumRpcUrl;
        private Integer _stratumRpcPort;

        private Integer _tlsPort;
        private String _tlsKeyFile;
        private String _tlsCertificateFile;

        public Integer getPort() { return _port; }
        public String getRootDirectory() { return _rootDirectory; }

        public String getBitcoinRpcUrl() { return _bitcoinRpcUrl; }
        public Integer getBitcoinRpcPort() { return _bitcoinRpcPort; }

        public String getStratumRpcUrl() { return _stratumRpcUrl; }
        public Integer getStratumRpcPort() { return _stratumRpcPort; }

        public Integer getTlsPort() { return _tlsPort; }
        public String getTlsKeyFile() { return _tlsKeyFile; }
        public String getTlsCertificateFile() { return _tlsCertificateFile; }
    }

    public static class StratumProperties {
        private Integer _port;
        private Integer _rpcPort;
        private String _bitcoinRpcUrl;
        private Integer _bitcoinRpcPort;
        private DatabaseProperties _databaseProperties;

        private Integer _httpPort;
        private String _rootDirectory;
        private Integer _tlsPort;
        private String _tlsKeyFile;
        private String _tlsCertificateFile;
        private String _cookiesDirectory;

        public Integer getPort() { return _port; }
        public Integer getRpcPort() { return _rpcPort; }
        public String getBitcoinRpcUrl() { return _bitcoinRpcUrl; }
        public Integer getBitcoinRpcPort() { return _bitcoinRpcPort; }
        public DatabaseProperties getDatabaseProperties() { return _databaseProperties; }

        public Integer getHttpPort() { return _httpPort; }
        public String getRootDirectory() { return _rootDirectory; }
        public Integer getTlsPort() { return _tlsPort; }
        public String getTlsKeyFile() { return _tlsKeyFile; }
        public String getTlsCertificateFile() { return _tlsCertificateFile; }
        public String getCookiesDirectory() { return _cookiesDirectory; }
    }

    public static class ProxyProperties {
        private Integer _httpPort;
        private Integer _externalTlsPort;
        private Integer _tlsPort;
        private String[] _tlsKeyFiles;
        private String[] _tlsCertificateFiles;

        public Integer getHttpPort() { return _httpPort; }
        public Integer getTlsPort() { return _tlsPort; }
        public Integer getExternalTlsPort() { return _externalTlsPort; }
        public String[] getTlsKeyFiles() { return _tlsKeyFiles; }
        public String[] getTlsCertificateFiles() { return _tlsCertificateFiles; }
    }

    public static class WalletProperties {
        private Integer _port;
        private String _rootDirectory;

        private Integer _tlsPort;
        private String _tlsKeyFile;
        private String _tlsCertificateFile;

        public Integer getPort() { return _port; }
        public String getRootDirectory() { return _rootDirectory; }

        public Integer getTlsPort() { return _tlsPort; }
        public String getTlsKeyFile() { return _tlsKeyFile; }
        public String getTlsCertificateFile() { return _tlsCertificateFile; }
    }

    private final Properties _properties;
    private BitcoinProperties _bitcoinProperties;
    private ExplorerProperties _explorerProperties;
    private StratumProperties _stratumProperties;
    private WalletProperties _walletProperties;
    private ProxyProperties _proxyProperties;

    private DatabaseProperties _loadDatabaseProperties(final String prefix) {
        final String propertyPrefix = (prefix == null ? "" : (prefix + "."));
        final String rootPassword = _properties.getProperty(propertyPrefix + "database.rootPassword", "d3d4a3d0533e3e83bc16db93414afd96");
        final String hostname = _properties.getProperty(propertyPrefix + "database.hostname", "");
        final String username = _properties.getProperty(propertyPrefix + "database.username", "root");
        final String password = _properties.getProperty(propertyPrefix + "database.password", "");
        final String schema = (_properties.getProperty(propertyPrefix + "database.schema", "bitcoin")).replaceAll("[^A-Za-z0-9_]", "");
        final Integer port = Util.parseInt(_properties.getProperty(propertyPrefix + "database.port", "8336"));
        final String dataDirectory = _properties.getProperty(propertyPrefix + "database.dataDirectory", "data");
        final Long maxMemoryByteCount = Util.parseLong(_properties.getProperty(propertyPrefix + "database.maxMemoryByteCount", String.valueOf(2L * ByteUtil.Unit.GIGABYTES)));
        final Boolean useEmbeddedDatabase = Util.parseBool(_properties.getProperty(propertyPrefix + "database.useEmbeddedDatabase", "1"));

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
        return databaseProperties;
    }

    private void _loadBitcoinProperties() {
        _bitcoinProperties = new BitcoinProperties();
        _bitcoinProperties._bitcoinPort = Util.parseInt(_properties.getProperty("bitcoin.port", BITCOIN_PORT.toString()));
        _bitcoinProperties._bitcoinRpcPort = Util.parseInt(_properties.getProperty("bitcoin.rpcPort", BITCOIN_RPC_PORT.toString()));

        final Json seedNodesJson = Json.parse(_properties.getProperty("bitcoin.seedNodes", "[\"btc.softwareverde.com\"]"));
        _bitcoinProperties._seedNodeProperties = new SeedNodeProperties[seedNodesJson.length()];
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

            _bitcoinProperties._seedNodeProperties[i] = seedNodeProperties;
        }

        _bitcoinProperties._maxPeerCount = Util.parseInt(_properties.getProperty("bitcoin.maxPeerCount", "24"));
        _bitcoinProperties._maxThreadCount = Util.parseInt(_properties.getProperty("bitcoin.maxThreadCount", "4"));
        _bitcoinProperties._trustedBlockHeight = Util.parseLong(_properties.getProperty("bitcoin.trustedBlockHeight", "0"));
        _bitcoinProperties._shouldSkipNetworking = Util.parseBool(_properties.getProperty("bitcoin.skipNetworking", "0"));
        _bitcoinProperties._maxUtxoCacheByteCount = Util.parseLong(_properties.getProperty("bitcoin.maxUtxoCacheByteCount", String.valueOf(512L * ByteUtil.Unit.MEGABYTES)));
        _bitcoinProperties._useTransactionBloomFilter = Util.parseBool(_properties.getProperty("bitcoin.useTransactionBloomFilter", "1"));
        _bitcoinProperties._shouldTrimBlocks = Util.parseBool(_properties.getProperty("bitcoin.trimBlocks", "0"));
        _bitcoinProperties._maxMessagesPerSecond = Util.parseInt(_properties.getProperty("bitcoin.maxMessagesPerSecondPerNode", "250"));

        _bitcoinProperties._databaseProperties = _loadDatabaseProperties("bitcoin");
    }

    private void _loadExplorerProperties() {
        final Integer port = Util.parseInt(_properties.getProperty("explorer.httpPort", EXPLORER_HTTP_PORT.toString()));
        final String rootDirectory = _properties.getProperty("explorer.rootDirectory", "explorer/www");

        final String bitcoinRpcUrl = _properties.getProperty("explorer.bitcoinRpcUrl", "");
        final Integer bitcoinRpcPort = Util.parseInt(_properties.getProperty("explorer.bitcoinRpcPort", BITCOIN_RPC_PORT.toString()));

        final String stratumRpcUrl = _properties.getProperty("explorer.stratumRpcUrl", "");
        final Integer stratumRpcPort = Util.parseInt(_properties.getProperty("explorer.stratumRpcPort", STRATUM_RPC_PORT.toString()));

        final Integer tlsPort = Util.parseInt(_properties.getProperty("explorer.tlsPort", EXPLORER_TLS_PORT.toString()));
        final String tlsKeyFile = _properties.getProperty("explorer.tlsKeyFile", "");
        final String tlsCertificateFile = _properties.getProperty("explorer.tlsCertificateFile", "");

        final ExplorerProperties explorerProperties = new ExplorerProperties();
        explorerProperties._port = port;
        explorerProperties._rootDirectory = rootDirectory;

        explorerProperties._bitcoinRpcUrl = bitcoinRpcUrl;
        explorerProperties._bitcoinRpcPort = bitcoinRpcPort;

        explorerProperties._stratumRpcUrl = stratumRpcUrl;
        explorerProperties._stratumRpcPort = stratumRpcPort;

        explorerProperties._tlsPort = tlsPort;
        explorerProperties._tlsKeyFile = (tlsKeyFile.isEmpty() ? null : tlsKeyFile);
        explorerProperties._tlsCertificateFile = (tlsCertificateFile.isEmpty() ? null : tlsCertificateFile);

        _explorerProperties = explorerProperties;
    }

    private void _loadStratumProperties() {
        final Integer port = Util.parseInt(_properties.getProperty("stratum.port", STRATUM_PORT.toString()));
        final Integer rpcPort = Util.parseInt(_properties.getProperty("stratum.rpcPort", STRATUM_RPC_PORT.toString()));
        final String bitcoinRpcUrl = _properties.getProperty("stratum.bitcoinRpcUrl", "");
        final Integer bitcoinRpcPort = Util.parseInt(_properties.getProperty("stratum.bitcoinRpcPort", BITCOIN_RPC_PORT.toString()));
        final Integer httpPort = Util.parseInt(_properties.getProperty("stratum.httpPort", STRATUM_HTTP_PORT.toString()));
        final Integer tlsPort = Util.parseInt(_properties.getProperty("stratum.tlsPort", STRATUM_TLS_PORT.toString()));
        final String rootDirectory = _properties.getProperty("stratum.rootDirectory", "stratum/www");
        final String tlsKeyFile = _properties.getProperty("stratum.tlsKeyFile", "");
        final String tlsCertificateFile = _properties.getProperty("stratum.tlsCertificateFile", "");
        final String cookiesDirectory = _properties.getProperty("stratum.cookiesDirectory", "tmp");

        final StratumProperties stratumProperties = new StratumProperties();
        stratumProperties._port = port;
        stratumProperties._rpcPort = rpcPort;
        stratumProperties._bitcoinRpcUrl = bitcoinRpcUrl;
        stratumProperties._bitcoinRpcPort = bitcoinRpcPort;

        stratumProperties._databaseProperties = _loadDatabaseProperties("stratum");

        stratumProperties._rootDirectory = rootDirectory;
        stratumProperties._httpPort = httpPort;
        stratumProperties._tlsPort = tlsPort;
        stratumProperties._tlsKeyFile = (tlsKeyFile.isEmpty() ? null : tlsKeyFile);
        stratumProperties._tlsCertificateFile = (tlsCertificateFile.isEmpty() ? null : tlsCertificateFile);
        stratumProperties._cookiesDirectory = cookiesDirectory;

        _stratumProperties = stratumProperties;
    }

    private void _loadWalletProperties() {
        final Integer port = Util.parseInt(_properties.getProperty("wallet.port", WALLET_PORT.toString()));
        final String rootDirectory = _properties.getProperty("wallet.rootDirectory", "wallet/www");

        final Integer tlsPort = Util.parseInt(_properties.getProperty("wallet.tlsPort", WALLET_TLS_PORT.toString()));
        final String tlsKeyFile = _properties.getProperty("wallet.tlsKeyFile", "");
        final String tlsCertificateFile = _properties.getProperty("wallet.tlsCertificateFile", "");

        final WalletProperties walletProperties = new WalletProperties();
        walletProperties._port = port;
        walletProperties._rootDirectory = rootDirectory;

        walletProperties._tlsPort = tlsPort;
        walletProperties._tlsKeyFile = (tlsKeyFile.isEmpty() ? null : tlsKeyFile);
        walletProperties._tlsCertificateFile = (tlsCertificateFile.isEmpty() ? null : tlsCertificateFile);

        _walletProperties = walletProperties;
    }

    protected String[] _getArrayStringProperty(final String propertyName) {
        final String arrayString = _properties.getProperty(propertyName, "[]").trim();
        final List<String> matches = new ArrayList<String>();

        final int startingIndex;
        final int length;
        if (arrayString.startsWith("[") && arrayString.endsWith("]")) {
            startingIndex = 1;
            length = (arrayString.length() - 1);
        }
        else {
            startingIndex = 0;
            length = arrayString.length();
        }

        final StringBuilder stringBuilder = new StringBuilder();
        for (int i = startingIndex; i < length; ++i) {
            final char c = arrayString.charAt(i);

            if (c == ',') {
                matches.add(stringBuilder.toString().trim());
                stringBuilder.setLength(0);
                continue;
            }

            stringBuilder.append(c);
        }
        if (stringBuilder.length() > 0) {
            matches.add(stringBuilder.toString().trim());
        }

        return matches.toArray(new String[0]);
    }

    private void _loadProxyProperties() {
        final Integer httpPort = Util.parseInt(_properties.getProperty("proxy.httpPort", PROXY_HTTP_PORT.toString()));
        final Integer tlsPort = Util.parseInt(_properties.getProperty("proxy.tlsPort", PROXY_TLS_PORT.toString()));
        final Integer externalTlsPort = Util.parseInt(_properties.getProperty("proxy.externalTlsPort", tlsPort.toString()));

        final String[] tlsKeyFiles = _getArrayStringProperty("proxy.tlsKeyFiles");
        final String[] tlsCertificateFiles = _getArrayStringProperty("proxy.tlsCertificateFiles");

        final ProxyProperties proxyProperties = new ProxyProperties();
        proxyProperties._httpPort = httpPort;
        proxyProperties._tlsPort = tlsPort;
        proxyProperties._externalTlsPort = externalTlsPort;
        proxyProperties._tlsKeyFiles = tlsKeyFiles;
        proxyProperties._tlsCertificateFiles = tlsCertificateFiles;

        _proxyProperties = proxyProperties;
    }

    public Configuration(final File configurationFile) {
        _properties = new Properties();

        try (final FileInputStream fileInputStream = new FileInputStream(configurationFile)) {
            _properties.load(fileInputStream);
        }
        catch (final IOException exception) {
            Logger.log("NOTICE: Unable to load properties.");
        }

        _loadBitcoinProperties();
        _loadStratumProperties();
        _loadExplorerProperties();
        _loadWalletProperties();
        _loadProxyProperties();
    }

    public BitcoinProperties getBitcoinProperties() { return _bitcoinProperties; }
    public ExplorerProperties getExplorerProperties() { return _explorerProperties; }
    public StratumProperties getStratumProperties() { return _stratumProperties; }
    public WalletProperties getWalletProperties() { return _walletProperties; }
    public ProxyProperties getProxyProperties() { return _proxyProperties; }

}
