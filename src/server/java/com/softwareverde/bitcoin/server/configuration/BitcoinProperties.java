package com.softwareverde.bitcoin.server.configuration;

import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputDatabaseManager;
import com.softwareverde.constable.list.List;
import com.softwareverde.logging.LogLevel;
import com.softwareverde.util.Util;

public class BitcoinProperties {
    public static final String DATA_DIRECTORY_NAME = "network";
    public static final Integer PORT = 8333;
    public static final Integer RPC_PORT = 8334;

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
    protected Boolean _shouldPrioritizeNewPeers;
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
    public Boolean shouldSkipNetworking() { return _shouldSkipNetworking; }
    public Boolean shouldPrioritizeNewPeers() { return _shouldPrioritizeNewPeers; }
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
}
