package com.softwareverde.bitcoin.server.configuration;

import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputDatabaseManager;
import com.softwareverde.constable.list.List;
import com.softwareverde.logging.LogLevel;
import com.softwareverde.util.Util;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class BitcoinProperties {
    public static final String DATA_DIRECTORY_NAME = "network";
    public static final Integer PORT = 8333;
    public static final Integer RPC_PORT = 8334;

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
    public static final String DATA_DIRECTORY = "bitcoin.dataDirectory";
    public static final String SHOULD_RELAY_INVALID_SLP_TRANSACTIONS = "bitcoin.relayInvalidSlpTransactions";
    public static final String DELETE_PENDING_BLOCKS_IS_ENABLED = "bitcoin.deletePendingBlocks";
    public static final String LOG_DIRECTORY = "bitcoin.logDirectory";
    public static final String LOG_LEVEL = "bitcoin.logLevel";
    public static final String TEST_NET = "bitcoin.testNet";

    protected Integer _bitcoinPort;
    protected Integer _testNetworkBitcoinPort;
    protected Integer _bitcoinRpcPort;
    protected Integer _testNetworkRpcPort;
    protected List<NodeProperties> _seedNodeProperties;
    protected List<NodeProperties> _testNetSeedNodeProperties;
    protected List<String> _dnsSeeds;
    protected List<String> _testNetDnsSeeds;
    protected List<String> _userAgentBlacklist;
    protected List<NodeProperties> _nodeWhitelist;
    protected Boolean _banFilterIsEnabled;
    protected Integer _minPeerCount;
    protected Integer _maxPeerCount;
    protected Integer _maxThreadCount;
    protected Long _trustedBlockHeight;
    protected Boolean _shouldSkipNetworking;
    protected Long _maxUtxoCacheByteCount;
    protected Long _utxoCommitFrequency;
    protected Float _utxoPurgePercent;
    protected Boolean _bootstrapIsEnabled;
    protected Boolean _shouldReIndexPendingBlocks;
    protected Boolean _indexingModeIsEnabled;
    protected Integer _maxMessagesPerSecond;
    protected String _dataDirectory;
    protected Boolean _shouldRelayInvalidSlpTransactions;
    protected Boolean _deletePendingBlocksIsEnabled;
    protected String _logDirectory;
    protected LogLevel _logLevel;
    protected Integer _testNet;

    protected Boolean _isTestNet() {
        return (Util.coalesce(_testNet) > 0);
    }

    public Integer getBitcoinPort() {
        final Integer defaultNetworkPort = BitcoinConstants.getDefaultNetworkPort();
        final Integer defaultTestNetworkPort = BitcoinConstants.getDefaultTestNetworkPort();

        if (_isTestNet()) {
            return Util.coalesce(_testNetworkBitcoinPort, defaultTestNetworkPort);
        }

        return Util.coalesce(_bitcoinPort, defaultNetworkPort);
    }

    public Integer getBitcoinRpcPort() {
        final Integer defaultRpcPort = BitcoinConstants.getDefaultRpcPort();
        final Integer defaultTestRpcPort = BitcoinConstants.getDefaultTestRpcPort();

        if (_isTestNet()) {
            return Util.coalesce(_testNetworkRpcPort, defaultTestRpcPort);
        }

        return Util.coalesce(_bitcoinRpcPort, defaultRpcPort);
    }

    public List<NodeProperties> getSeedNodeProperties() { return (_isTestNet() ? _testNetSeedNodeProperties : _seedNodeProperties); }
    public List<String> getDnsSeeds() { return (_isTestNet() ? _testNetDnsSeeds : _dnsSeeds); }

    /**
     * Returns a list of IPs/Hosts that should never be added to the Ban Filter.
     *  NodeProperties.getPort() of a whitelisted Node is always null.
     */
    public List<NodeProperties> getNodeWhitelist() { return _nodeWhitelist; }

    public List<String> getUserAgentBlacklist() { return _userAgentBlacklist; }
    public Boolean isBanFilterEnabled() { return _banFilterIsEnabled; }
    public Integer getMinPeerCount() { return _minPeerCount; }
    public Integer getMaxPeerCount() { return _maxPeerCount; }
    public Integer getMaxThreadCount() { return _maxThreadCount; }
    public Long getTrustedBlockHeight() { return _trustedBlockHeight; }
    public Boolean skipNetworking() { return _shouldSkipNetworking; }
    public Boolean isDeletePendingBlocksEnabled() { return _deletePendingBlocksIsEnabled; }
    public String getLogDirectory() { return _logDirectory; }
    public LogLevel getLogLevel() { return _logLevel; }
    public Boolean isTestNet() { return _isTestNet(); }
    public Integer getTestNet() { return _testNet; }

    public Long getMaxUtxoCacheByteCount() { return _maxUtxoCacheByteCount; }
    public Long getMaxCachedUtxoCount() {
        final Long maxUtxoCacheWithDoubleBufferByteCount = (_maxUtxoCacheByteCount / 2L); // Disk-writes are double-buffered, using up to double the size in the worst-case scenario.
        return (maxUtxoCacheWithDoubleBufferByteCount / UnspentTransactionOutputDatabaseManager.BYTES_PER_UTXO);
    }
    public Long getUtxoCacheCommitFrequency() { return _utxoCommitFrequency; }
    public Float getUtxoCachePurgePercent() { return _utxoPurgePercent; }

    public Boolean isIndexingModeEnabled() { return _indexingModeIsEnabled; }
    public Integer getMaxMessagesPerSecond() { return _maxMessagesPerSecond; }
    public Boolean isBootstrapEnabled() { return (_isTestNet() ? false : _bootstrapIsEnabled); }
    public Boolean shouldReIndexPendingBlocks() { return _shouldReIndexPendingBlocks; } // May be null if unset.
    public String getDataDirectory() { return _dataDirectory; }
    public Boolean isInvalidSlpTransactionRelayEnabled() { return _shouldRelayInvalidSlpTransactions; }

    public Map<String, String> toConfigurationMap() {
        return new LinkedHashMap<String, String>() {{
            put(BITCOIN_PORT, _coalesce(_bitcoinPort));
            put(TEST_NETWORK_BITCOIN_PORT, _coalesce(_testNetworkBitcoinPort));
            put(BITCOIN_RPC_PORT, _coalesce(_bitcoinRpcPort));
            put(TEST_NETWORK_RPC_PORT, _coalesce(_testNetworkRpcPort));
            put(DNS_SEEDS, PropertiesUtil.stringListToConfigurationFileProperty(_dnsSeeds));
            put(TEST_NET_DNS_SEEDS, PropertiesUtil.stringListToConfigurationFileProperty(_testNetDnsSeeds));
            put(USER_AGENT_BLACKLIST, PropertiesUtil.stringListToConfigurationFileProperty(_userAgentBlacklist));
            put(NODE_WHITE_LIST, PropertiesUtil.nodePropertiesToConfigurationFileProperty(_nodeWhitelist));
            put(BAN_FILTER_IS_ENABLED, _coalesce(_banFilterIsEnabled));
            put(MIN_PEER_COUNT, _coalesce(_minPeerCount));
            put(MAX_PEER_COUNT, _coalesce(_maxPeerCount));
            put(MAX_THREAD_COUNT, _coalesce(_maxThreadCount));
            put(TRUSTED_BLOCK_HEIGHT, _coalesce(_trustedBlockHeight));
            put(SHOULD_SKIP_NETWORKING, _coalesce(_shouldSkipNetworking));
            put(MAX_UTXO_CACHE_BYTE_COUNT, _coalesce(_maxUtxoCacheByteCount));
            put(UTXO_COMMIT_FREQUENCY, _coalesce(_utxoCommitFrequency));
            put(UTXO_PURGE_PERCENT, _coalesce(_utxoPurgePercent));
            put(BOOTSTRAP_IS_ENABLED, _coalesce(_bootstrapIsEnabled));
            put(SHOULD_REINDEX_PENDING_BLOCKS, _coalesce(_shouldReIndexPendingBlocks));
            put(INDEXING_MODE_IS_ENABLED, _coalesce(_indexingModeIsEnabled));
            put(MAX_MESSAGES_PER_SECOND, _coalesce(_maxMessagesPerSecond));
            put(DATA_DIRECTORY, _dataDirectory);
            put(SHOULD_RELAY_INVALID_SLP_TRANSACTIONS, _coalesce(_shouldRelayInvalidSlpTransactions));
            put(DELETE_PENDING_BLOCKS_IS_ENABLED, _coalesce(_deletePendingBlocksIsEnabled));
            put(LOG_DIRECTORY, _logDirectory);
            put(LOG_LEVEL, _logLevel.name());
            put(TEST_NET, _coalesce(_testNet));
        }};
    }

    public String _coalesce(final Object property) {
        if (property == null) {
            return "";
        }

        if (property instanceof Boolean) {
            return (Boolean) property ? "1" : "0";
        }

        return property.toString();
    }
}
