package com.softwareverde.bitcoin.server.configuration;

import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputDatabaseManager;
import com.softwareverde.constable.list.List;
import com.softwareverde.logging.LogLevel;

public class BitcoinProperties {
    public static final String DATA_DIRECTORY_NAME = "network";
    public static final Integer PORT = 8333;
    public static final Integer RPC_PORT = 8334;

    protected Integer _bitcoinPort;
    protected Integer _bitcoinRpcPort;
    protected List<SeedNodeProperties> _seedNodeProperties;
    protected List<String> _dnsSeeds;
    protected List<String> _userAgentBlacklist;
    protected List<SeedNodeProperties> _nodesWhitelist;
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

    public Integer getBitcoinPort() { return _bitcoinPort; }
    public Integer getBitcoinRpcPort() { return _bitcoinRpcPort; }
    public List<SeedNodeProperties> getSeedNodeProperties() { return _seedNodeProperties; }
    public List<String> getDnsSeeds() { return _dnsSeeds; }
    public List<String> getUserAgentBlacklist() { return _userAgentBlacklist; }
    public List<SeedNodeProperties> getNodeWhitelist() { return _nodesWhitelist; }
    public Boolean isBanFilterEnabled() { return _banFilterIsEnabled; }
    public Integer getMinPeerCount() { return _minPeerCount; }
    public Integer getMaxPeerCount() { return _maxPeerCount; }
    public Integer getMaxThreadCount() { return _maxThreadCount; }
    public Long getTrustedBlockHeight() { return _trustedBlockHeight; }
    public Boolean skipNetworking() { return _shouldSkipNetworking; }
    public Boolean isDeletePendingBlocksEnabled() { return _deletePendingBlocksIsEnabled; }
    public String getLogDirectory() { return _logDirectory; }
    public LogLevel getLogLevel() { return _logLevel; }

    public Long getMaxUtxoCacheByteCount() { return _maxUtxoCacheByteCount; }
    public Long getMaxCachedUtxoCount() {
        final Long maxUtxoCacheWithDoubleBufferByteCount = (_maxUtxoCacheByteCount / 2L); // Disk-writes are double-buffered, using up to double the size in the worst-case scenario.
        return (maxUtxoCacheWithDoubleBufferByteCount / UnspentTransactionOutputDatabaseManager.BYTES_PER_UTXO);
    }
    public Long getUtxoCacheCommitFrequency() { return _utxoCommitFrequency; }
    public Float getUtxoCachePurgePercent() { return _utxoPurgePercent; }

    public Boolean isIndexingModeEnabled() { return _indexingModeIsEnabled; }
    public Integer getMaxMessagesPerSecond() { return _maxMessagesPerSecond; }
    public Boolean isBootstrapEnabled() { return _bootstrapIsEnabled; }
    public Boolean shouldReIndexPendingBlocks() { return _shouldReIndexPendingBlocks; } // May be null if unset.
    public String getDataDirectory() { return _dataDirectory; }
    public Boolean isInvalidSlpTransactionRelayEnabled() { return _shouldRelayInvalidSlpTransactions; }
}