package com.softwareverde.bitcoin.server.configuration;

import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputDatabaseManager;
import com.softwareverde.constable.list.List;
import com.softwareverde.logging.LogLevel;
import com.softwareverde.util.Util;

public class BitcoinProperties {
    public static final String DATA_CACHE_DIRECTORY_NAME = "cache"; // TODO
    public static final Integer PORT = 8333;
    public static final Integer RPC_PORT = 8334;
    public static final Integer TEST_NET_PORT = 18333;

    protected Integer _bitcoinPort;
    protected Integer _testNetBitcoinPort;
    protected Integer _bitcoinRpcPort;
    protected List<NodeProperties> _seedNodeProperties;
    protected List<NodeProperties> _testNetSeedNodeProperties;
    protected List<String> _dnsSeeds;
    protected List<String> _testNetDnsSeeds;
    protected List<NodeProperties> _whitelistedNodes;
    protected Boolean _banFilterIsEnabled;
    protected Integer _maxPeerCount;
    protected Integer _maxThreadCount;
    protected Long _trustedBlockHeight;
    protected Boolean _shouldSkipNetworking;
    protected Long _maxUtxoCacheByteCount;
    protected Long _utxoCommitFrequency;
    protected Float _utxoPurgePercent;
    protected Boolean _bootstrapIsEnabled;
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

    public Integer getBitcoinPort() { return (_isTestNet() ? _testNetBitcoinPort : _bitcoinPort); }
    public Integer getBitcoinRpcPort() { return _bitcoinRpcPort; }
    public List<NodeProperties> getSeedNodeProperties() { return (_isTestNet() ? _testNetSeedNodeProperties : _seedNodeProperties); }
    public List<String> getDnsSeeds() { return (_isTestNet() ? _testNetDnsSeeds : _dnsSeeds); }
    public List<NodeProperties> getWhitelistedNodes() { return _whitelistedNodes; }
    public Boolean isBanFilterEnabled() { return _banFilterIsEnabled; }
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
        return (_maxUtxoCacheByteCount / UnspentTransactionOutputDatabaseManager.BYTES_PER_UTXO);
    }
    public Long getUtxoCacheCommitFrequency() { return _utxoCommitFrequency; }
    public Float getUtxoCachePurgePercent() { return _utxoPurgePercent; }

    public Boolean isIndexingModeEnabled() { return _indexingModeIsEnabled; }
    public Integer getMaxMessagesPerSecond() { return _maxMessagesPerSecond; }
    public Boolean isBootstrapEnabled() { return (_isTestNet() ? false : _bootstrapIsEnabled); }
    public String getDataDirectory() { return _dataDirectory; }
    public Boolean isInvalidSlpTransactionRelayEnabled() { return _shouldRelayInvalidSlpTransactions; }
}