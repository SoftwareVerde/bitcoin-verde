package com.softwareverde.bitcoin.server.configuration;

import com.softwareverde.bitcoin.rpc.RpcCredentials;
import com.softwareverde.constable.map.Map;
import com.softwareverde.constable.map.mutable.MutableLinkedHashMap;
import com.softwareverde.constable.map.mutable.MutableMap;
import com.softwareverde.util.IoUtil;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.Util;

import java.io.File;
import java.nio.charset.StandardCharsets;


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
    public static final String FAST_SYNC_TIMEOUT = "bitcoin.fastSyncTimeoutSeconds";
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
        for (final Tuple<String, String> entry : propertiesMap) {
            final String key = entry.first;
            final String value = entry.second;

            final String exportedValue = Util.coalesce(userInputMap.get(key), value);
            if (exportedValue.isEmpty()) {
                ConfigurationPropertiesExporter.appendPropertyToStringBuilder(stringBuilder, key, value);
                return;
            }

            ConfigurationPropertiesExporter.appendPropertyToStringBuilder(stringBuilder, key, exportedValue);
        }
        stringBuilder.append("\n");
    }

    protected static void appendPropertyToStringBuilder(final StringBuilder stringBuilder, final String key, final String propertyValue) {
        stringBuilder.append(key).append(" = ").append(propertyValue).append("\n");
    }

    protected static Map<String, String> bitcoinVerdeDatabasePropertiesToConfigurationMap(final String prefix, final BitcoinVerdeDatabaseProperties bitcoinVerdeDatabaseProperties) {
        final MutableMap<String, String> map = new MutableLinkedHashMap<>();
        map.put(prefix + ROOT_PASSWORD, bitcoinVerdeDatabaseProperties.getRootPassword());
        map.put(prefix + HOST_NAME, bitcoinVerdeDatabaseProperties.getHostname());
        map.put(prefix + USERNAME, bitcoinVerdeDatabaseProperties.getUsername());
        map.put(prefix + PASSWORD, bitcoinVerdeDatabaseProperties.getPassword());
        map.put(prefix + SCHEMA, bitcoinVerdeDatabaseProperties.getSchema());
        map.put(prefix + PORT, ConfigurationPropertiesExporter.coalesce(bitcoinVerdeDatabaseProperties.getPort()));

        map.put(prefix + SHOULD_USE_EMBEDDED_DATABASE, ConfigurationPropertiesExporter.coalesce(bitcoinVerdeDatabaseProperties._shouldUseEmbeddedDatabase));
        map.put(prefix + MAX_MEMORY_BYTE_COUNT, ConfigurationPropertiesExporter.coalesce(bitcoinVerdeDatabaseProperties._maxMemoryByteCount));
        map.put(prefix + LOG_FILE_BYTE_COUNT, ConfigurationPropertiesExporter.coalesce(bitcoinVerdeDatabaseProperties._logFileByteCount));
        map.put(prefix + DATABASE_PROPERTIES_DATA_DIRECTORY, bitcoinVerdeDatabaseProperties._dataDirectory.getPath());
        map.put(prefix + INSTALLATION_DIRECTORY, bitcoinVerdeDatabaseProperties._installationDirectory.getPath());
        return map;
    }

    protected static Map<String, String> bitcoinPropertiesToConfigurationMap(final BitcoinProperties bitcoinProperties) {
        final MutableMap<String, String> map = new MutableLinkedHashMap<>();
        map.put(BITCOIN_PORT, ConfigurationPropertiesExporter.coalesce(bitcoinProperties._bitcoinPort));
        map.put(TEST_NETWORK_BITCOIN_PORT, ConfigurationPropertiesExporter.coalesce(bitcoinProperties._testNetworkBitcoinPort));
        map.put(BITCOIN_RPC_PORT, ConfigurationPropertiesExporter.coalesce(bitcoinProperties._bitcoinRpcPort));
        map.put(TEST_NETWORK_RPC_PORT, ConfigurationPropertiesExporter.coalesce(bitcoinProperties._testNetworkRpcPort));
        map.put(DNS_SEEDS, PropertiesUtil.stringListToConfigurationFileProperty(bitcoinProperties._dnsSeeds));
        map.put(TEST_NET_DNS_SEEDS, PropertiesUtil.stringListToConfigurationFileProperty(bitcoinProperties._testNetDnsSeeds));
        map.put(USER_AGENT_BLACKLIST, PropertiesUtil.stringListToConfigurationFileProperty(bitcoinProperties._userAgentBlacklist));
        map.put(NODE_WHITE_LIST, PropertiesUtil.nodePropertiesToConfigurationFileProperty(bitcoinProperties._nodeWhitelist));
        map.put(BAN_FILTER_IS_ENABLED, ConfigurationPropertiesExporter.coalesce(bitcoinProperties._banFilterIsEnabled));
        map.put(MIN_PEER_COUNT, ConfigurationPropertiesExporter.coalesce(bitcoinProperties._minPeerCount));
        map.put(MAX_PEER_COUNT, ConfigurationPropertiesExporter.coalesce(bitcoinProperties._maxPeerCount));
        map.put(MAX_THREAD_COUNT, ConfigurationPropertiesExporter.coalesce(bitcoinProperties._maxThreadCount));
        map.put(TRUSTED_BLOCK_HEIGHT, ConfigurationPropertiesExporter.coalesce(bitcoinProperties._trustedBlockHeight));
        map.put(SHOULD_SKIP_NETWORKING, ConfigurationPropertiesExporter.coalesce(bitcoinProperties._shouldSkipNetworking));
        map.put(MAX_UTXO_CACHE_BYTE_COUNT, ConfigurationPropertiesExporter.coalesce(bitcoinProperties._maxUtxoCacheByteCount));
        map.put(UTXO_COMMIT_FREQUENCY, ConfigurationPropertiesExporter.coalesce(bitcoinProperties._utxoCommitFrequency));
        map.put(UTXO_PURGE_PERCENT, ConfigurationPropertiesExporter.coalesce(bitcoinProperties._utxoPurgePercent));
        map.put(BOOTSTRAP_IS_ENABLED, ConfigurationPropertiesExporter.coalesce(bitcoinProperties._bootstrapIsEnabled));
        map.put(FAST_SYNC_IS_ENABLED, ConfigurationPropertiesExporter.coalesce(bitcoinProperties._fastSyncIsEnabled));
        map.put(FAST_SYNC_TIMEOUT, ConfigurationPropertiesExporter.coalesce(bitcoinProperties._fastSyncTimeoutInSeconds));
        map.put(INDEXING_MODE_IS_ENABLED, ConfigurationPropertiesExporter.coalesce(bitcoinProperties._indexingModeIsEnabled));
        map.put(MAX_MESSAGES_PER_SECOND, ConfigurationPropertiesExporter.coalesce(bitcoinProperties._maxMessagesPerSecond));
        map.put(BITCOIN_PROPERTIES_DATA_DIRECTORY, bitcoinProperties._dataDirectory);
        map.put(SHOULD_RELAY_INVALID_SLP_TRANSACTIONS, ConfigurationPropertiesExporter.coalesce(bitcoinProperties._shouldRelayInvalidSlpTransactions));
        map.put(DELETE_PENDING_BLOCKS_IS_ENABLED, ConfigurationPropertiesExporter.coalesce(bitcoinProperties._deletePendingBlocksIsEnabled));
        map.put(LOG_DIRECTORY, bitcoinProperties._logDirectory);
        map.put(LOG_LEVEL, bitcoinProperties._logLevel.name());
        map.put(TEST_NET, ConfigurationPropertiesExporter.coalesce(bitcoinProperties._testNet));
        return map;
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

        final MutableMap<String, String> map = new MutableLinkedHashMap<>();
        map.put(STRATUM_PORT, ConfigurationPropertiesExporter.coalesce(stratumProperties._port));
        map.put(STRATUM_RPC_PORT, ConfigurationPropertiesExporter.coalesce(stratumProperties._rpcPort));
        map.put(STRATUM_BITCOIN_RPC_URL, ConfigurationPropertiesExporter.coalesce(stratumProperties._bitcoinRpcUrl));
        map.put(STRATUM_BITCOIN_RPC_USERNAME, ConfigurationPropertiesExporter.coalesce(rpcUsername));
        map.put(STRATUM_BITCOIN_RPC_PASSWORD, ConfigurationPropertiesExporter.coalesce(rpcPassword));
        map.put(STRATUM_BITCOIN_RPC_PORT, ConfigurationPropertiesExporter.coalesce(stratumProperties._bitcoinRpcPort));
        map.put(STRATUM_HTTP_PORT, ConfigurationPropertiesExporter.coalesce(stratumProperties._httpPort));
        map.put(STRATUM_TLS_PORT, ConfigurationPropertiesExporter.coalesce(stratumProperties._tlsPort));
        map.put(STRATUM_ROOT_DIRECTORY, ConfigurationPropertiesExporter.coalesce(stratumProperties._rootDirectory));
        map.put(STRATUM_TLS_KEY_FILE, ConfigurationPropertiesExporter.coalesce(stratumProperties._tlsKeyFile));
        map.put(STRATUM_TLS_CERTIFICATE_FILE, ConfigurationPropertiesExporter.coalesce(stratumProperties._tlsCertificateFile));
        map.put(STRATUM_COOKIES_DIRECTORY, ConfigurationPropertiesExporter.coalesce(stratumProperties._cookiesDirectory));
        map.put(STRATUM_SECURE_COOKIES, ConfigurationPropertiesExporter.coalesce(stratumProperties._useSecureCookies));
        return map;
    }

    protected static Map<String, String> explorerPropertiesToConfigurationMap(final ExplorerProperties explorerProperties) {
        final MutableMap<String, String> map = new MutableLinkedHashMap<>();
        map.put(EXPLORER_PORT, ConfigurationPropertiesExporter.coalesce(explorerProperties._port));
        map.put(EXPLORER_ROOT_DIRECTORY, ConfigurationPropertiesExporter.coalesce(explorerProperties._rootDirectory));
        map.put(EXPLORER_BITCOIN_RPC_URL, ConfigurationPropertiesExporter.coalesce(explorerProperties._bitcoinRpcUrl));
        map.put(EXPLORER_BITCOIN_RPC_PORT, ConfigurationPropertiesExporter.coalesce(explorerProperties._bitcoinRpcPort));
        map.put(EXPLORER_STRATUM_RPC_URL, ConfigurationPropertiesExporter.coalesce(explorerProperties._stratumRpcUrl));
        map.put(EXPLORER_STRATUM_RPC_PORT, ConfigurationPropertiesExporter.coalesce(explorerProperties._stratumRpcPort));
        map.put(EXPLORER_TLS_PORT, ConfigurationPropertiesExporter.coalesce(explorerProperties._tlsPort));
        map.put(EXPLORER_TLS_KEY_FILE, ConfigurationPropertiesExporter.coalesce(explorerProperties._tlsKeyFile));
        map.put(EXPLORER_TLS_CERTIFICATE_FILE, ConfigurationPropertiesExporter.coalesce(explorerProperties._tlsCertificateFile));
        return map;
    }

    protected static Map<String, String> walletPropertiesToConfigurationMap(final WalletProperties walletProperties) {
        final MutableMap<String, String> map = new MutableLinkedHashMap<>();
        map.put(WALLET_PORT, ConfigurationPropertiesExporter.coalesce(walletProperties._port));
        map.put(WALLET_ROOT_DIRECTORY, ConfigurationPropertiesExporter.coalesce(walletProperties._rootDirectory));
        map.put(WALLET_TLS_PORT, ConfigurationPropertiesExporter.coalesce(walletProperties._tlsPort));
        map.put(WALLET_TLS_KEY_FILE, ConfigurationPropertiesExporter.coalesce(walletProperties._tlsKeyFile));
        map.put(WALLET_TLS_CERTIFICATE_FILE, ConfigurationPropertiesExporter.coalesce(walletProperties._tlsCertificateFile));
        return map;
    }

    protected static Map<String, String> proxyPropertiesToConfigurationMap(final ProxyProperties proxyProperties) {
        final MutableMap<String, String> map = new MutableLinkedHashMap<>();
        map.put(PROXY_HTTP_PORT, ConfigurationPropertiesExporter.coalesce(proxyProperties._httpPort));
        map.put(PROXY_TLS_PORT, ConfigurationPropertiesExporter.coalesce(proxyProperties._tlsPort));
        map.put(PROXY_EXTERNAL_TLS_PORT, ConfigurationPropertiesExporter.coalesce(proxyProperties._externalTlsPort));
        map.put(PROXY_EXTERNAL_TLS_KEY_FILES, PropertiesUtil.stringListToConfigurationFileProperty(proxyProperties._tlsKeyFiles));
        map.put(PROXY_EXTERNAL_TLS_CERTIFICATE_FILES, PropertiesUtil.stringListToConfigurationFileProperty(proxyProperties._tlsCertificateFiles));
        return map;
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
