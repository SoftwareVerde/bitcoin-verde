package com.softwareverde.bitcoin.server.configuration;

import java.util.LinkedHashMap;
import java.util.Map;

public class PropertiesExporter {
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
    public static final String SHOULD_REINDEX_PENDING_BLOCKS = "bitcoin.reIndexPendingBlocks";
    public static final String INDEXING_MODE_IS_ENABLED = "bitcoin.indexBlocks";
    public static final String MAX_MESSAGES_PER_SECOND = "bitcoin.maxMessagesPerSecondPerNode";
    public static final String BITCOIN_PROPERTIES_DATA_DIRECTORY = "bitcoin.dataDirectory";
    public static final String SHOULD_RELAY_INVALID_SLP_TRANSACTIONS = "bitcoin.relayInvalidSlpTransactions";
    public static final String DELETE_PENDING_BLOCKS_IS_ENABLED = "bitcoin.deletePendingBlocks";
    public static final String LOG_DIRECTORY = "bitcoin.logDirectory";
    public static final String LOG_LEVEL = "bitcoin.logLevel";
    public static final String TEST_NET = "bitcoin.testNet";

    public static Map<String, String> configurationPropertiesToMap(final Configuration configuration) {
        return new LinkedHashMap<String, String>() {{
            putAll(_bitcoinPropertiesToConfigurationMap(configuration.getBitcoinProperties()));
            putAll(_bitcoinVerdeDatabasePropertiesToConfigurationMap(configuration.getBitcoinDatabaseProperties()));
        }};
    }

    private static Map<String, String> _bitcoinVerdeDatabasePropertiesToConfigurationMap(final BitcoinVerdeDatabaseProperties bitcoinVerdeDatabaseProperties) {
        final String prefix = "bitcoin.";
        return new LinkedHashMap<String, String>() {{
           put(prefix + ROOT_PASSWORD, bitcoinVerdeDatabaseProperties.getRootPassword());
           put(prefix + HOST_NAME, bitcoinVerdeDatabaseProperties.getHostname());
           put(prefix + USERNAME, bitcoinVerdeDatabaseProperties.getUsername());
           put(prefix + PASSWORD, bitcoinVerdeDatabaseProperties.getPassword());
           put(prefix + SCHEMA, bitcoinVerdeDatabaseProperties.getSchema());
           put(prefix + PORT, _coalesce(bitcoinVerdeDatabaseProperties.getPort()));

           put(prefix + SHOULD_USE_EMBEDDED_DATABASE, _coalesce(bitcoinVerdeDatabaseProperties._shouldUseEmbeddedDatabase));
           put(prefix + MAX_MEMORY_BYTE_COUNT, _coalesce(bitcoinVerdeDatabaseProperties._maxMemoryByteCount));
           put(prefix + LOG_FILE_BYTE_COUNT, _coalesce(bitcoinVerdeDatabaseProperties._logFileByteCount));
           put(prefix + DATABASE_PROPERTIES_DATA_DIRECTORY, bitcoinVerdeDatabaseProperties._dataDirectory.getPath());
           put(prefix + INSTALLATION_DIRECTORY, bitcoinVerdeDatabaseProperties._installationDirectory.getPath());
        }};
    }

    private static Map<String, String> _bitcoinPropertiesToConfigurationMap(final BitcoinProperties bitcoinProperties) {
        return new LinkedHashMap<String, String>() {{
            put(BITCOIN_PORT, _coalesce(bitcoinProperties._bitcoinPort));
            put(TEST_NETWORK_BITCOIN_PORT, _coalesce(bitcoinProperties._testNetworkBitcoinPort));
            put(BITCOIN_RPC_PORT, _coalesce(bitcoinProperties._bitcoinRpcPort));
            put(TEST_NETWORK_RPC_PORT, _coalesce(bitcoinProperties._testNetworkRpcPort));
            put(DNS_SEEDS, PropertiesUtil.stringListToConfigurationFileProperty(bitcoinProperties._dnsSeeds));
            put(TEST_NET_DNS_SEEDS, PropertiesUtil.stringListToConfigurationFileProperty(bitcoinProperties._testNetDnsSeeds));
            put(USER_AGENT_BLACKLIST, PropertiesUtil.stringListToConfigurationFileProperty(bitcoinProperties._userAgentBlacklist));
            put(NODE_WHITE_LIST, PropertiesUtil.nodePropertiesToConfigurationFileProperty(bitcoinProperties._nodeWhitelist));
            put(BAN_FILTER_IS_ENABLED, _coalesce(bitcoinProperties._banFilterIsEnabled));
            put(MIN_PEER_COUNT, _coalesce(bitcoinProperties._minPeerCount));
            put(MAX_PEER_COUNT, _coalesce(bitcoinProperties._maxPeerCount));
            put(MAX_THREAD_COUNT, _coalesce(bitcoinProperties._maxThreadCount));
            put(TRUSTED_BLOCK_HEIGHT, _coalesce(bitcoinProperties._trustedBlockHeight));
            put(SHOULD_SKIP_NETWORKING, _coalesce(bitcoinProperties._shouldSkipNetworking));
            put(MAX_UTXO_CACHE_BYTE_COUNT, _coalesce(bitcoinProperties._maxUtxoCacheByteCount));
            put(UTXO_COMMIT_FREQUENCY, _coalesce(bitcoinProperties._utxoCommitFrequency));
            put(UTXO_PURGE_PERCENT, _coalesce(bitcoinProperties._utxoPurgePercent));
            put(BOOTSTRAP_IS_ENABLED, _coalesce(bitcoinProperties._bootstrapIsEnabled));
            put(SHOULD_REINDEX_PENDING_BLOCKS, _coalesce(bitcoinProperties._shouldReIndexPendingBlocks));
            put(INDEXING_MODE_IS_ENABLED, _coalesce(bitcoinProperties._indexingModeIsEnabled));
            put(MAX_MESSAGES_PER_SECOND, _coalesce(bitcoinProperties._maxMessagesPerSecond));
            put(BITCOIN_PROPERTIES_DATA_DIRECTORY, bitcoinProperties._dataDirectory);
            put(SHOULD_RELAY_INVALID_SLP_TRANSACTIONS, _coalesce(bitcoinProperties._shouldRelayInvalidSlpTransactions));
            put(DELETE_PENDING_BLOCKS_IS_ENABLED, _coalesce(bitcoinProperties._deletePendingBlocksIsEnabled));
            put(LOG_DIRECTORY, bitcoinProperties._logDirectory);
            put(LOG_LEVEL, bitcoinProperties._logLevel.name());
            put(TEST_NET, _coalesce(bitcoinProperties._testNet));
        }};
    }

    public static String _coalesce(final Object property) {
        if (property == null) {
            return "";
        }

        if (property instanceof Boolean) {
            return (Boolean) property ? "1" : "0";
        }

        return property.toString();
    }
}
