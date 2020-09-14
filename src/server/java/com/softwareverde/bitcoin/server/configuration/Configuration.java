package com.softwareverde.bitcoin.server.configuration;

import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputDatabaseManager;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.json.Json;
import com.softwareverde.logging.LogLevel;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.Util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class Configuration {
    protected final Properties _properties;

    protected BitcoinProperties _bitcoinProperties;
    protected DatabaseProperties _bitcoinDatabaseProperties;

    protected ExplorerProperties _explorerProperties;

    protected StratumProperties _stratumProperties;
    protected DatabaseProperties _stratumDatabaseProperties;

    protected WalletProperties _walletProperties;
    protected ProxyProperties _proxyProperties;

    protected DatabaseProperties _loadDatabaseProperties(final String prefix) {
        final String propertyPrefix = (prefix == null ? "" : (prefix + "."));
        final String rootPassword = _properties.getProperty(propertyPrefix + "database.rootPassword", "d3d4a3d0533e3e83bc16db93414afd96");
        final String hostname = _properties.getProperty(propertyPrefix + "database.hostname", "");
        final String username = _properties.getProperty(propertyPrefix + "database.username", "root");
        final String password = _properties.getProperty(propertyPrefix + "database.password", "");
        final String schema = (_properties.getProperty(propertyPrefix + "database.schema", "bitcoin")).replaceAll("[^A-Za-z0-9_]", "");
        final Integer port = Util.parseInt(_properties.getProperty(propertyPrefix + "database.port", "8336"));
        final String dataDirectory = _properties.getProperty(propertyPrefix + "database.dataDirectory", "data");
        final Boolean useEmbeddedDatabase = Util.parseBool(_properties.getProperty(propertyPrefix + "database.useEmbeddedDatabase", "1"));
        final Long maxMemoryByteCount = Util.parseLong(_properties.getProperty(propertyPrefix + "database.maxMemoryByteCount", String.valueOf(2L * ByteUtil.Unit.Binary.GIBIBYTES)));

        // According to https://www.percona.com/blog/2008/11/21/how-to-calculate-a-good-innodb-log-file-size/, and running the calculation on a trimming node syncing yr 2015, the GB/hr was about 6.5gb.
        // In lieu of this, the default value was decided to be set to 8GB to better accommodate slightly higher loads.
        final Long logFileByteCount = Util.parseLong(_properties.getProperty(propertyPrefix + "database.logFileByteCount", String.valueOf(512 * ByteUtil.Unit.Binary.MEBIBYTES)));

        final DatabaseProperties databaseProperties = new DatabaseProperties();
        databaseProperties.setRootPassword(rootPassword);
        databaseProperties.setHostname(hostname);
        databaseProperties.setUsername(username);
        databaseProperties.setPassword(password);
        databaseProperties.setSchema(schema);
        databaseProperties.setPort(port);
        databaseProperties.setDataDirectory(dataDirectory);
        databaseProperties._useEmbeddedDatabase = useEmbeddedDatabase;
        databaseProperties._maxMemoryByteCount = maxMemoryByteCount;
        databaseProperties._logFileByteCount = logFileByteCount;
        return databaseProperties;
    }

    protected NodeProperties[] _parseSeedNodeProperties(final String propertyName, final String defaultValue) {
        final Json seedNodesJson = Json.parse(_properties.getProperty(propertyName, defaultValue));
        final NodeProperties[] nodePropertiesArray = new NodeProperties[seedNodesJson.length()];
        for (int i = 0; i < seedNodesJson.length(); ++i) {
            final String propertiesString = seedNodesJson.getString(i);

            final NodeProperties nodeProperties;
            final int indexOfColon = propertiesString.indexOf(":");
            if (indexOfColon < 0) {
                nodeProperties = new NodeProperties(propertiesString, BitcoinConstants.getDefaultNetworkPort());
            }
            else {
                final String address = propertiesString.substring(0, indexOfColon);
                final Integer port = Util.parseInt(propertiesString.substring(indexOfColon + 1));
                nodeProperties = new NodeProperties(address, port);
            }

            nodePropertiesArray[i] = nodeProperties;
        }
        return nodePropertiesArray;
    }

    protected void _loadBitcoinProperties() {
        _bitcoinProperties = new BitcoinProperties();
        _bitcoinProperties._bitcoinPort = Util.parseInt(_properties.getProperty("bitcoin.port", BitcoinConstants.getDefaultNetworkPort().toString()));
        _bitcoinProperties._bitcoinRpcPort = Util.parseInt(_properties.getProperty("bitcoin.rpcPort", BitcoinConstants.getDefaultRpcPort().toString()));

        { // Parse Seed Nodes...
            final NodeProperties[] nodeProperties = _parseSeedNodeProperties("bitcoin.seedNodes", "[\"btc.softwareverde.com\", \"bitcoinverde.org\"]");
            _bitcoinProperties._seedNodeProperties = new ImmutableList<NodeProperties>(nodeProperties);
        }

        { // Parse DNS Seed Nodes...
            final String defaultSeedsString = "[\"seed.flowee.cash\", \"seed-bch.bitcoinforks.org\", \"btccash-seeder.bitcoinunlimited.info\", \"seed.bchd.cash\"]";
            final Json seedNodesJson = Json.parse(_properties.getProperty("bitcoin.dnsSeeds", defaultSeedsString));

            final MutableList<String> dnsSeeds = new MutableList<String>(seedNodesJson.length());
            for (int i = 0; i < seedNodesJson.length(); ++i) {
                final String seedHost = seedNodesJson.getString(i);
                if (seedHost != null) {
                    dnsSeeds.add(seedHost);
                }
            }

            _bitcoinProperties._dnsSeeds = dnsSeeds;
        }

        { // Parse TestNet Seed Nodes...
            final NodeProperties[] nodeProperties = _parseSeedNodeProperties("bitcoin.testNetSeedNodes", "[]");
            _bitcoinProperties._testNetSeedNodeProperties = new ImmutableList<NodeProperties>(nodeProperties);
        }

        { // Parse TestNet DNS Seed Nodes...
            final String defaultSeedsString = "[\"testnet-seed-bch.bitcoinforks.org\", \"testnet-seed.bchd.cash\"]";
            final Json seedNodesJson = Json.parse(_properties.getProperty("bitcoin.testNetDnsSeeds", defaultSeedsString));

            final MutableList<String> dnsSeeds = new MutableList<String>(seedNodesJson.length());
            for (int i = 0; i < seedNodesJson.length(); ++i) {
                final String seedHost = seedNodesJson.getString(i);
                if (seedHost != null) {
                    dnsSeeds.add(seedHost);
                }
            }

            _bitcoinProperties._testNetDnsSeeds = dnsSeeds;
        }

        { // Parse Whitelisted Nodes...
            final NodeProperties[] nodeProperties = _parseSeedNodeProperties("bitcoin.whitelistedNodes", "[]");
            _bitcoinProperties._whitelistedNodes = new ImmutableList<NodeProperties>(nodeProperties);
        }

        _bitcoinProperties._banFilterIsEnabled = Util.parseBool(_properties.getProperty("bitcoin.enableBanFilter", "1"));
        _bitcoinProperties._maxPeerCount = Util.parseInt(_properties.getProperty("bitcoin.maxPeerCount", "24"));
        _bitcoinProperties._maxThreadCount = Util.parseInt(_properties.getProperty("bitcoin.maxThreadCount", "4"));
        _bitcoinProperties._trustedBlockHeight = Util.parseLong(_properties.getProperty("bitcoin.trustedBlockHeight", "0"));
        _bitcoinProperties._shouldSkipNetworking = Util.parseBool(_properties.getProperty("bitcoin.skipNetworking", "0"));
        _bitcoinProperties._deletePendingBlocksIsEnabled = Util.parseBool(_properties.getProperty("bitcoin.deletePendingBlocks", "1"));
        _bitcoinProperties._maxUtxoCacheByteCount = Util.parseLong(_properties.getProperty("bitcoin.maxUtxoCacheByteCount", String.valueOf(UnspentTransactionOutputDatabaseManager.DEFAULT_MAX_UTXO_CACHE_COUNT * UnspentTransactionOutputDatabaseManager.BYTES_PER_UTXO)));
        _bitcoinProperties._utxoCommitFrequency = Util.parseLong(_properties.getProperty("bitcoin.utxoCommitFrequency", "2016"));
        _bitcoinProperties._logDirectory = _properties.getProperty("bitcoin.logDirectory", "logs");
        _bitcoinProperties._logLevel = LogLevel.fromString(_properties.getProperty("bitcoin.logLevel", "INFO"));

        _bitcoinProperties._utxoPurgePercent = Util.parseFloat(_properties.getProperty("bitcoin.utxoPurgePercent", String.valueOf(UnspentTransactionOutputDatabaseManager.DEFAULT_PURGE_PERCENT)));
        if (_bitcoinProperties._utxoPurgePercent < 0F) {
            _bitcoinProperties._utxoPurgePercent = 0F;
        }
        else if (_bitcoinProperties._utxoPurgePercent > 1F) {
            _bitcoinProperties._utxoPurgePercent = 1F;
        }

        _bitcoinProperties._bootstrapIsEnabled = Util.parseBool(_properties.getProperty("bitcoin.enableBootstrap", "1"));
        _bitcoinProperties._indexingModeIsEnabled = Util.parseBool(_properties.getProperty("bitcoin.indexBlocks", "1"));
        _bitcoinProperties._maxMessagesPerSecond = Util.parseInt(_properties.getProperty("bitcoin.maxMessagesPerSecondPerNode", "250"));
        _bitcoinProperties._dataDirectory = _properties.getProperty("bitcoin.dataDirectory", "data");
        _bitcoinProperties._shouldRelayInvalidSlpTransactions = Util.parseBool(_properties.getProperty("bitcoin.relayInvalidSlpTransactions", "1"));

        _bitcoinProperties._testNet = Util.parseInt(_properties.getProperty("bitcoin.testNet", "0"));
        _bitcoinProperties._testNetBitcoinPort = Util.parseInt(_properties.getProperty("bitcoin.testNetPort", BitcoinConstants.TestNet.defaultNetworkPort.toString()));
    }

    protected void _loadExplorerProperties() {
        final Integer port = Util.parseInt(_properties.getProperty("explorer.httpPort", ExplorerProperties.HTTP_PORT.toString()));
        final String rootDirectory = _properties.getProperty("explorer.rootDirectory", "explorer/www");

        final String bitcoinRpcUrl = _properties.getProperty("explorer.bitcoinRpcUrl", "");
        final Integer bitcoinRpcPort = Util.parseInt(_properties.getProperty("explorer.bitcoinRpcPort", BitcoinConstants.MainNet.defaultRpcPort.toString()));

        final String stratumRpcUrl = _properties.getProperty("explorer.stratumRpcUrl", "");
        final Integer stratumRpcPort = Util.parseInt(_properties.getProperty("explorer.stratumRpcPort", StratumProperties.RPC_PORT.toString()));

        final Integer tlsPort = Util.parseInt(_properties.getProperty("explorer.tlsPort", ExplorerProperties.TLS_PORT.toString()));
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

    protected void _loadStratumProperties() {
        final Integer port = Util.parseInt(_properties.getProperty("stratum.port", StratumProperties.PORT.toString()));
        final Integer rpcPort = Util.parseInt(_properties.getProperty("stratum.rpcPort", StratumProperties.RPC_PORT.toString()));
        final String bitcoinRpcUrl = _properties.getProperty("stratum.bitcoinRpcUrl", "");
        final Integer bitcoinRpcPort = Util.parseInt(_properties.getProperty("stratum.bitcoinRpcPort", BitcoinConstants.MainNet.defaultRpcPort.toString()));
        final Integer httpPort = Util.parseInt(_properties.getProperty("stratum.httpPort", StratumProperties.HTTP_PORT.toString()));
        final Integer tlsPort = Util.parseInt(_properties.getProperty("stratum.tlsPort", StratumProperties.TLS_PORT.toString()));
        final String rootDirectory = _properties.getProperty("stratum.rootDirectory", "stratum/www");
        final String tlsKeyFile = _properties.getProperty("stratum.tlsKeyFile", "");
        final String tlsCertificateFile = _properties.getProperty("stratum.tlsCertificateFile", "");
        final String cookiesDirectory = _properties.getProperty("stratum.cookiesDirectory", "tmp");

        final StratumProperties stratumProperties = new StratumProperties();
        stratumProperties._port = port;
        stratumProperties._rpcPort = rpcPort;
        stratumProperties._bitcoinRpcUrl = bitcoinRpcUrl;
        stratumProperties._bitcoinRpcPort = bitcoinRpcPort;

        stratumProperties._rootDirectory = rootDirectory;
        stratumProperties._httpPort = httpPort;
        stratumProperties._tlsPort = tlsPort;
        stratumProperties._tlsKeyFile = (tlsKeyFile.isEmpty() ? null : tlsKeyFile);
        stratumProperties._tlsCertificateFile = (tlsCertificateFile.isEmpty() ? null : tlsCertificateFile);
        stratumProperties._cookiesDirectory = cookiesDirectory;

        _stratumProperties = stratumProperties;
    }

    protected void _loadWalletProperties() {
        final Integer port = Util.parseInt(_properties.getProperty("wallet.port", WalletProperties.PORT.toString()));
        final String rootDirectory = _properties.getProperty("wallet.rootDirectory", "wallet/www");

        final Integer tlsPort = Util.parseInt(_properties.getProperty("wallet.tlsPort", WalletProperties.TLS_PORT.toString()));
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

    protected void _loadProxyProperties() {
        final Integer httpPort = Util.parseInt(_properties.getProperty("proxy.httpPort", ProxyProperties.HTTP_PORT.toString()));
        final Integer tlsPort = Util.parseInt(_properties.getProperty("proxy.tlsPort", ProxyProperties.TLS_PORT.toString()));
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
            Logger.warn("Unable to load properties.");
        }

        _bitcoinDatabaseProperties = _loadDatabaseProperties("bitcoin");
        _stratumDatabaseProperties = _loadDatabaseProperties("stratum");

        _loadBitcoinProperties();
        _loadStratumProperties();
        _loadExplorerProperties();
        _loadWalletProperties();
        _loadProxyProperties();
    }

    public BitcoinProperties getBitcoinProperties() { return _bitcoinProperties; }
    public DatabaseProperties getBitcoinDatabaseProperties() { return _bitcoinDatabaseProperties; }

    public ExplorerProperties getExplorerProperties() { return _explorerProperties; }

    public StratumProperties getStratumProperties() { return _stratumProperties; }
    public DatabaseProperties getStratumDatabaseProperties() { return _stratumDatabaseProperties; }

    public WalletProperties getWalletProperties() { return _walletProperties; }
    public ProxyProperties getProxyProperties() { return _proxyProperties; }

}
