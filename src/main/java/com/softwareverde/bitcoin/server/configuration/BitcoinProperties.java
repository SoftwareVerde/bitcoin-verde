package com.softwareverde.bitcoin.server.configuration;

import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputDatabaseManager;
import com.softwareverde.logging.LogLevel;
import com.softwareverde.util.Util;

public class BitcoinProperties {
    public static final String DATA_CACHE_DIRECTORY_NAME = "cache";
    public static final Integer PORT = 8333;
    public static final Integer RPC_PORT = 8334;

    protected Integer _bitcoinPort;
    protected Integer _bitcoinRpcPort;
    protected SeedNodeProperties[] _seedNodeProperties;
    protected SeedNodeProperties[] _whitelistedNodes;
    protected Boolean _banFilterIsEnabled;
    protected Integer _maxPeerCount;
    protected Integer _maxThreadCount;
    protected Long _trustedBlockHeight;
    protected Boolean _shouldSkipNetworking;
    protected Long _maxUtxoCacheByteCount;
    protected Long _utxoCommitFrequency;
    protected Float _utxoPurgePercent;
    protected Boolean _bootstrapIsEnabled;
    protected Boolean _trimBlocksIsEnabled;
    protected Boolean _indexingModeIsEnabled;
    protected Boolean _blockCacheIsEnabled;
    protected Integer _maxMessagesPerSecond;
    protected String _dataDirectory;
    protected Boolean _shouldRelayInvalidSlpTransactions;
    protected Boolean _deletePendingBlocksIsEnabled;
    protected LogLevel _logLevel;

    public Integer getBitcoinPort() { return _bitcoinPort; }
    public Integer getBitcoinRpcPort() { return _bitcoinRpcPort; }
    public SeedNodeProperties[] getSeedNodeProperties() { return Util.copyArray(_seedNodeProperties); }
    public SeedNodeProperties[] getWhitelistedNodes() { return Util.copyArray(_whitelistedNodes); }
    public Boolean isBanFilterEnabled() { return _banFilterIsEnabled; }
    public Integer getMaxPeerCount() { return _maxPeerCount; }
    public Integer getMaxThreadCount() { return _maxThreadCount; }
    public Long getTrustedBlockHeight() { return _trustedBlockHeight; }
    public Boolean skipNetworking() { return _shouldSkipNetworking; }
    public Boolean isDeletePendingBlocksEnabled() { return _deletePendingBlocksIsEnabled; }
    public LogLevel getLogLevel() { return _logLevel; }

    public Long getMaxUtxoCacheByteCount() { return _maxUtxoCacheByteCount; }
    public Long getMaxCachedUtxoCount() {
        return (_maxUtxoCacheByteCount / UnspentTransactionOutputDatabaseManager.BYTES_PER_UTXO);
    }
    public Long getUtxoCacheCommitFrequency() { return _utxoCommitFrequency; }
    public Float getUtxoCachePurgePercent() { return _utxoPurgePercent; }

    public Boolean isTrimBlocksEnabled() { return _trimBlocksIsEnabled; }
    public Boolean isIndexingModeEnabled() { return _indexingModeIsEnabled; }
    public Boolean isBlockCacheEnabled() { return _blockCacheIsEnabled; }
    public Integer getMaxMessagesPerSecond() { return _maxMessagesPerSecond; }
    public Boolean isBootstrapEnabled() { return _bootstrapIsEnabled; }
    public String getDataDirectory() { return _dataDirectory; }
    public Boolean isInvalidSlpTransactionRelayEnabled() { return _shouldRelayInvalidSlpTransactions; }
}