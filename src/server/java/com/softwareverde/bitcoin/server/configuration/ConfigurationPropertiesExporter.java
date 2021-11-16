package com.softwareverde.bitcoin.server.configuration;

import com.softwareverde.bitcoin.rpc.RpcCredentials;
import com.softwareverde.util.IoUtil;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class ConfigurationPropertiesExporter {
    // MutableDatabaseProperties
    public static final String ROOT_PASSWORD = "database.rootPassword";
    public static final String HOST_NAME = "database.hostname";
    public static final String USERNAME = "database.username";
    public static final String PASSWORD = "database.password";
    public static final String SCHEMA = "database.schema";
    public static final String PORT = "database.port";

    // Additional database properties
    public static final String  SHOULD_USE_EMBEDDED_DATABASE = "database.useEmbeddedDatabase";
    public static final String  MAX_MEMORY_BYTE_COUNT = "database.maxMemoryByteCount";
    public static final String  LOG_FILE_BYTE_COUNT = "database.logFileByteCount";
    public static final String DATABASE_PROPERTIES_DATA_DIRECTORY = "database.dataDirectory";
    public static final String  INSTALLATION_DIRECTORY = "database.installationDirectory";

    // BitcoinProperties
    public static final String BITCOIN_PORT = "bitcoin.port";
    public static final String TEST_NETWORK_BITCOIN_PORT = "bitcoin.testNetPort";
    public static final String BITCOIN_RPC_PORT = "bitcoin.rpcPort";
    public static final String TEST_NETWORK_RPC_PORT = "bitcoin.testNetRpcPort";
    public static final String DNS_SEEDS = "bitcoin.dnsSeeds";
    public static final String TEST_NET_DNS_SEEDS = "bitcoin.testNetDnsSeeds";
    public static final String USER_AGENT_BLACKLIST = "bitcoin.userAgentBlacklist";
    public static final String NODE_WHITE_LIST = "bitcoin.whitelistedNodes";
    public static final String BAN_FILTER_IS_ENABLED = "bitcoin.enableBanFilter";
    public static final String MIN_PEER_COUNT = "bitcoin.minPeerCount";
    public static final String MAX_PEER_COUNT = "bitcoin.maxPeerCount";
    public static final String MAX_THREAD_COUNT = "bitcoin.maxThreadCount";
    public static final String TRUSTED_BLOCK_HEIGHT = "bitcoin.trustedBlockHeight";
    public static final String SHOULD_SKIP_NETWORKING = "bitcoin.skipNetworking";
    public static final String MAX_UTXO_CACHE_BYTE_COUNT = "bitcoin.maxUtxoCacheByteCount";
    public static final String UTXO_COMMIT_FREQUENCY = "bitcoin.utxoCommitFrequency";
    public static final String UTXO_PURGE_PERCENT = "bitcoin.utxoPurgePercent";
    public static final String BOOTSTRAP_IS_ENABLED = "bitcoin.enableBootstrap";
    public static final String FAST_SYNC_IS_ENABLED = "bitcoin.enableFastSync";
    public static final String INDEXING_MODE_IS_ENABLED = "bitcoin.indexBlocks";
    public static final String MAX_MESSAGES_PER_SECOND = "bitcoin.maxMessagesPerSecondPerNode";
    public static final String BITCOIN_PROPERTIES_DATA_DIRECTORY = "bitcoin.dataDirectory";
    public static final String SHOULD_RELAY_INVALID_SLP_TRANSACTIONS = "bitcoin.relayInvalidSlpTransactions";
    public static final String DELETE_PENDING_BLOCKS_IS_ENABLED = "bitcoin.deletePendingBlocks";
    public static final String LOG_DIRECTORY = "bitcoin.logDirectory";
    public static final String LOG_LEVEL = "bitcoin.logLevel";
    public static final String TEST_NET = "bitcoin.testNet";

    // StratumProperties
    public static final String STRATUM_PORT = "stratum.port";
    public static final String STRATUM_RPC_PORT = "stratum.rpcPort";
    public static final String STRATUM_BITCOIN_RPC_URL = "stratum.bitcoinRpcUrl";
    public static final String STRATUM_BITCOIN_RPC_USERNAME = "stratum.bitcoinRpcUsername";
    public static final String STRATUM_BITCOIN_RPC_PASSWORD = "stratum.bitcoinRpcPassword";
    public static final String STRATUM_BITCOIN_RPC_PORT = "stratum.bitcoinRpcPort";
    public static final String STRATUM_HTTP_PORT = "stratum.httpPort";
    public static final String STRATUM_TLS_PORT = "stratum.tlsPort";
    public static final String STRATUM_ROOT_DIRECTORY = "stratum.rootDirectory";
    public static final String STRATUM_TLS_KEY_FILE = "stratum.tlsKeyFile";
    public static final String STRATUM_TLS_CERTIFICATE_FILE = "stratum.tlsCertificateFile";
    public static final String STRATUM_COOKIES_DIRECTORY = "stratum.cookiesDirectory";
    public static final String STRATUM_SECURE_COOKIES = "stratum.secureCookies";

    // ExplorerProperties
    public static final String EXPLORER_PORT = "explorer.httpPort";
    public static final String EXPLORER_ROOT_DIRECTORY = "explorer.rootDirectory";
    public static final String EXPLORER_BITCOIN_RPC_URL = "explorer.bitcoinRpcUrl";
    public static final String EXPLORER_BITCOIN_RPC_PORT = "explorer.bitcoinRpcPort";
    public static final String EXPLORER_STRATUM_RPC_URL = "explorer.stratumRpcUrl";
    public static final String EXPLORER_STRATUM_RPC_PORT = "explorer.stratumRpcPort";
    public static final String EXPLORER_TLS_PORT = "explorer.tlsPort";
    public static final String EXPLORER_TLS_KEY_FILE = "explorer.tlsKeyFile";
    public static final String EXPLORER_TLS_CERTIFICATE_FILE = "explorer.tlsCertificateFile";

    // Wallet
    public static final String WALLET_PORT = "wallet.port";
    public static final String WALLET_ROOT_DIRECTORY = "wallet.rootDirectory";
    public static final String WALLET_TLS_PORT = "wallet.tlsPort";
    public static final String WALLET_TLS_KEY_FILE = "wallet.tlsKeyFile";
    public static final String WALLET_TLS_CERTIFICATE_FILE = "wallet.tlsCertificateFile";

    // Proxy
    public static final String PROXY_HTTP_PORT = "proxy.httpPort";
    public static final String PROXY_TLS_PORT = "proxy.tlsPort";
    public static final String PROXY_EXTERNAL_TLS_PORT = "proxy.externalTlsPort";
    public static final String PROXY_EXTERNAL_TLS_KEY_FILES = "proxy.tlsKeyFiles";
    public static final String PROXY_EXTERNAL_TLS_CERTIFICATE_FILES = "proxy.tlsCertificateFiles";

    protected static String coalesce(final Object property) {
        if (property == null) {
            return "";
        }

        if (property instanceof Boolean) {
            return (Boolean) property ? "1" : "0";
        }

        return property.toString();
    }

    protected static void appendPropertiesMapToStringBuilder(final StringBuilder stringBuilder, final Map<String, String> propertiesMap, final Map<String, String> userInputMap) {
        propertiesMap.forEach((key, value) -> {
            final String exportedValue = userInputMap.getOrDefault(key, value);

            if (exportedValue.isEmpty()) {
                ConfigurationPropertiesExporter.appendPropertyToStringBuilder(stringBuilder, key, value);
                return;
            }

            ConfigurationPropertiesExporter.appendPropertyToStringBuilder(stringBuilder, key, exportedValue);
        });
        stringBuilder.append("\n");
    }

    protected static void appendPropertyToStringBuilder(final StringBuilder stringBuilder, final String key, final String propertyValue) {
        stringBuilder.append(key).append(" = ").append(propertyValue).append("\n");
    }

    protected static Map<String, String> bitcoinVerdeDatabasePropertiesToConfigurationMap(final String prefix, final BitcoinVerdeDatabaseProperties bitcoinVerdeDatabaseProperties) {
        return new LinkedHashMap<String, String>() {{
           this.put(prefix + ROOT_PASSWORD, bitcoinVerdeDatabaseProperties.getRootPassword());
           this.put(prefix + HOST_NAME, bitcoinVerdeDatabaseProperties.getHostname());
           this.put(prefix + USERNAME, bitcoinVerdeDatabaseProperties.getUsername());
           this.put(prefix + PASSWORD, bitcoinVerdeDatabaseProperties.getPassword());
           this.put(prefix + SCHEMA, bitcoinVerdeDatabaseProperties.getSchema());
           this.put(prefix + PORT, ConfigurationPropertiesExporter.coalesce(bitcoinVerdeDatabaseProperties.getPort()));

           this.put(prefix + SHOULD_USE_EMBEDDED_DATABASE, ConfigurationPropertiesExporter.coalesce(bitcoinVerdeDatabaseProperties._shouldUseEmbeddedDatabase));
           this.put(prefix + MAX_MEMORY_BYTE_COUNT, ConfigurationPropertiesExporter.coalesce(bitcoinVerdeDatabaseProperties._maxMemoryByteCount));
           this.put(prefix + LOG_FILE_BYTE_COUNT, ConfigurationPropertiesExporter.coalesce(bitcoinVerdeDatabaseProperties._logFileByteCount));
           this.put(prefix + DATABASE_PROPERTIES_DATA_DIRECTORY, bitcoinVerdeDatabaseProperties._dataDirectory.getPath());
           this.put(prefix + INSTALLATION_DIRECTORY, bitcoinVerdeDatabaseProperties._installationDirectory.getPath());
        }};
    }

    protected static Map<String, String> bitcoinPropertiesToConfigurationMap(final BitcoinProperties bitcoinProperties) {
        return new LinkedHashMap<String, String>() {{
            this.put(BITCOIN_PORT, ConfigurationPropertiesExporter.coalesce(bitcoinProperties._bitcoinPort));
            this.put(TEST_NETWORK_BITCOIN_PORT, ConfigurationPropertiesExporter.coalesce(bitcoinProperties._testNetworkBitcoinPort));
            this.put(BITCOIN_RPC_PORT, ConfigurationPropertiesExporter.coalesce(bitcoinProperties._bitcoinRpcPort));
            this.put(TEST_NETWORK_RPC_PORT, ConfigurationPropertiesExporter.coalesce(bitcoinProperties._testNetworkRpcPort));
            this.put(DNS_SEEDS, PropertiesUtil.stringListToConfigurationFileProperty(bitcoinProperties._dnsSeeds));
            this.put(TEST_NET_DNS_SEEDS, PropertiesUtil.stringListToConfigurationFileProperty(bitcoinProperties._testNetDnsSeeds));
            this.put(USER_AGENT_BLACKLIST, PropertiesUtil.stringListToConfigurationFileProperty(bitcoinProperties._userAgentBlacklist));
            this.put(NODE_WHITE_LIST, PropertiesUtil.nodePropertiesToConfigurationFileProperty(bitcoinProperties._nodeWhitelist));
            this.put(BAN_FILTER_IS_ENABLED, ConfigurationPropertiesExporter.coalesce(bitcoinProperties._banFilterIsEnabled));
            this.put(MIN_PEER_COUNT, ConfigurationPropertiesExporter.coalesce(bitcoinProperties._minPeerCount));
            this.put(MAX_PEER_COUNT, ConfigurationPropertiesExporter.coalesce(bitcoinProperties._maxPeerCount));
            this.put(MAX_THREAD_COUNT, ConfigurationPropertiesExporter.coalesce(bitcoinProperties._maxThreadCount));
            this.put(TRUSTED_BLOCK_HEIGHT, ConfigurationPropertiesExporter.coalesce(bitcoinProperties._trustedBlockHeight));
            this.put(SHOULD_SKIP_NETWORKING, ConfigurationPropertiesExporter.coalesce(bitcoinProperties._shouldSkipNetworking));
            this.put(MAX_UTXO_CACHE_BYTE_COUNT, ConfigurationPropertiesExporter.coalesce(bitcoinProperties._maxUtxoCacheByteCount));
            this.put(UTXO_COMMIT_FREQUENCY, ConfigurationPropertiesExporter.coalesce(bitcoinProperties._utxoCommitFrequency));
            this.put(UTXO_PURGE_PERCENT, ConfigurationPropertiesExporter.coalesce(bitcoinProperties._utxoPurgePercent));
            this.put(BOOTSTRAP_IS_ENABLED, ConfigurationPropertiesExporter.coalesce(bitcoinProperties._bootstrapIsEnabled));
            this.put(FAST_SYNC_IS_ENABLED, ConfigurationPropertiesExporter.coalesce(bitcoinProperties._fastSyncIsEnabled));
            this.put(INDEXING_MODE_IS_ENABLED, ConfigurationPropertiesExporter.coalesce(bitcoinProperties._indexingModeIsEnabled));
            this.put(MAX_MESSAGES_PER_SECOND, ConfigurationPropertiesExporter.coalesce(bitcoinProperties._maxMessagesPerSecond));
            this.put(BITCOIN_PROPERTIES_DATA_DIRECTORY, bitcoinProperties._dataDirectory);
            this.put(SHOULD_RELAY_INVALID_SLP_TRANSACTIONS, ConfigurationPropertiesExporter.coalesce(bitcoinProperties._shouldRelayInvalidSlpTransactions));
            this.put(DELETE_PENDING_BLOCKS_IS_ENABLED, ConfigurationPropertiesExporter.coalesce(bitcoinProperties._deletePendingBlocksIsEnabled));
            this.put(LOG_DIRECTORY, bitcoinProperties._logDirectory);
            this.put(LOG_LEVEL, bitcoinProperties._logLevel.name());
            this.put(TEST_NET, ConfigurationPropertiesExporter.coalesce(bitcoinProperties._testNet));
        }};
    }

    protected static Map<String, String> stratumPropertiesToConfigurationMap(final StratumProperties stratumProperties) {
        final RpcCredentials rpcCredentials = stratumProperties.getRpcCredentials();
        final String rpcUsername;
        final String rpcPassword;
        if (rpcCredentials != null) {
            rpcUsername = rpcCredentials.getUsername();
            rpcPassword = rpcCredentials.getPassword();
        }
        else {
            rpcUsername = null;
            rpcPassword = null;
        }

        return new LinkedHashMap<String, String>() {{
            put(STRATUM_PORT, ConfigurationPropertiesExporter.coalesce(stratumProperties._port));
            put(STRATUM_RPC_PORT, ConfigurationPropertiesExporter.coalesce(stratumProperties._rpcPort));
            put(STRATUM_BITCOIN_RPC_URL, ConfigurationPropertiesExporter.coalesce(stratumProperties._bitcoinRpcUrl));
            put(STRATUM_BITCOIN_RPC_USERNAME, ConfigurationPropertiesExporter.coalesce(rpcUsername));
            put(STRATUM_BITCOIN_RPC_PASSWORD, ConfigurationPropertiesExporter.coalesce(rpcPassword));
            put(STRATUM_BITCOIN_RPC_PORT, ConfigurationPropertiesExporter.coalesce(stratumProperties._bitcoinRpcPort));
            put(STRATUM_HTTP_PORT, ConfigurationPropertiesExporter.coalesce(stratumProperties._httpPort));
            put(STRATUM_TLS_PORT, ConfigurationPropertiesExporter.coalesce(stratumProperties._tlsPort));
            put(STRATUM_ROOT_DIRECTORY, ConfigurationPropertiesExporter.coalesce(stratumProperties._rootDirectory));
            put(STRATUM_TLS_KEY_FILE, ConfigurationPropertiesExporter.coalesce(stratumProperties._tlsKeyFile));
            put(STRATUM_TLS_CERTIFICATE_FILE, ConfigurationPropertiesExporter.coalesce(stratumProperties._tlsCertificateFile));
            put(STRATUM_COOKIES_DIRECTORY, ConfigurationPropertiesExporter.coalesce(stratumProperties._cookiesDirectory));
            put(STRATUM_SECURE_COOKIES, ConfigurationPropertiesExporter.coalesce(stratumProperties._useSecureCookies));
        }};
    }

    protected static Map<String, String> explorerPropertiesToConfigurationMap(final ExplorerProperties explorerProperties) {
        return new LinkedHashMap<String, String>() {{
            put(EXPLORER_PORT, ConfigurationPropertiesExporter.coalesce(explorerProperties._port));
            put(EXPLORER_ROOT_DIRECTORY, ConfigurationPropertiesExporter.coalesce(explorerProperties._rootDirectory));
            put(EXPLORER_BITCOIN_RPC_URL, ConfigurationPropertiesExporter.coalesce(explorerProperties._bitcoinRpcUrl));
            put(EXPLORER_BITCOIN_RPC_PORT, ConfigurationPropertiesExporter.coalesce(explorerProperties._bitcoinRpcPort));
            put(EXPLORER_STRATUM_RPC_URL, ConfigurationPropertiesExporter.coalesce(explorerProperties._stratumRpcUrl));
            put(EXPLORER_STRATUM_RPC_PORT, ConfigurationPropertiesExporter.coalesce(explorerProperties._stratumRpcPort));
            put(EXPLORER_TLS_PORT, ConfigurationPropertiesExporter.coalesce(explorerProperties._tlsPort));
            put(EXPLORER_TLS_KEY_FILE, ConfigurationPropertiesExporter.coalesce(explorerProperties._tlsKeyFile));
            put(EXPLORER_TLS_CERTIFICATE_FILE, ConfigurationPropertiesExporter.coalesce(explorerProperties._tlsCertificateFile));
        }};
    }

    protected static Map<String, String> walletPropertiesToConfigurationMap(final WalletProperties walletProperties) {
        return new LinkedHashMap<String, String>() {{
            put(WALLET_PORT, ConfigurationPropertiesExporter.coalesce(walletProperties._port));
            put(WALLET_ROOT_DIRECTORY, ConfigurationPropertiesExporter.coalesce(walletProperties._rootDirectory));
            put(WALLET_TLS_PORT, ConfigurationPropertiesExporter.coalesce(walletProperties._tlsPort));
            put(WALLET_TLS_KEY_FILE, ConfigurationPropertiesExporter.coalesce(walletProperties._tlsKeyFile));
            put(WALLET_TLS_CERTIFICATE_FILE, ConfigurationPropertiesExporter.coalesce(walletProperties._tlsCertificateFile));
        }};
    }

    protected static Map<String, String> proxyPropertiesToConfigurationMap(final ProxyProperties proxyProperties) {
        return new LinkedHashMap<String, String>() {{
            put(PROXY_HTTP_PORT, ConfigurationPropertiesExporter.coalesce(proxyProperties._httpPort));
            put(PROXY_TLS_PORT, ConfigurationPropertiesExporter.coalesce(proxyProperties._tlsPort));
            put(PROXY_EXTERNAL_TLS_PORT, ConfigurationPropertiesExporter.coalesce(proxyProperties._externalTlsPort));
            put(PROXY_EXTERNAL_TLS_KEY_FILES, PropertiesUtil.stringListToConfigurationFileProperty(proxyProperties._tlsKeyFiles));
            put(PROXY_EXTERNAL_TLS_CERTIFICATE_FILES, PropertiesUtil.stringListToConfigurationFileProperty(proxyProperties._tlsCertificateFiles));
        }};
    }

    public static void exportConfiguration(final Configuration configuration, final String configurationFilename, final Map<String, String> userInputMap) {
        final StringBuilder stringBuilder = new StringBuilder();

        // Bitcoin
        ConfigurationPropertiesExporter.appendPropertiesMapToStringBuilder(stringBuilder, ConfigurationPropertiesExporter.bitcoinPropertiesToConfigurationMap(configuration.getBitcoinProperties()), userInputMap);
        ConfigurationPropertiesExporter.appendPropertiesMapToStringBuilder(stringBuilder, ConfigurationPropertiesExporter.bitcoinVerdeDatabasePropertiesToConfigurationMap("bitcoin.", configuration.getBitcoinDatabaseProperties()), userInputMap);

        // Explorer
        ConfigurationPropertiesExporter.appendPropertiesMapToStringBuilder(stringBuilder, ConfigurationPropertiesExporter.explorerPropertiesToConfigurationMap(configuration.getExplorerProperties()), userInputMap);

        // Stratum
        ConfigurationPropertiesExporter.appendPropertiesMapToStringBuilder(stringBuilder, ConfigurationPropertiesExporter.stratumPropertiesToConfigurationMap(configuration.getStratumProperties()), userInputMap);
        ConfigurationPropertiesExporter.appendPropertiesMapToStringBuilder(stringBuilder, ConfigurationPropertiesExporter.bitcoinVerdeDatabasePropertiesToConfigurationMap("stratum.", configuration.getStratumDatabaseProperties()), userInputMap);

        // SPV
        ConfigurationPropertiesExporter.appendPropertiesMapToStringBuilder(stringBuilder, ConfigurationPropertiesExporter.bitcoinVerdeDatabasePropertiesToConfigurationMap("spv.", configuration.getSpvDatabaseProperties()), userInputMap);

        // Wallet
        ConfigurationPropertiesExporter.appendPropertiesMapToStringBuilder(stringBuilder, ConfigurationPropertiesExporter.walletPropertiesToConfigurationMap(configuration.getWalletProperties()), userInputMap);

        // Proxy
        ConfigurationPropertiesExporter.appendPropertiesMapToStringBuilder(stringBuilder, ConfigurationPropertiesExporter.proxyPropertiesToConfigurationMap(configuration.getProxyProperties()), userInputMap);

        new File(configurationFilename).delete();
        IoUtil.putFileContents(configurationFilename, stringBuilder.toString().getBytes(StandardCharsets.UTF_8));
    }
}
