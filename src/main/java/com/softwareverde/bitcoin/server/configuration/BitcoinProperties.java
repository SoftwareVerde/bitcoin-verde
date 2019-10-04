package com.softwareverde.bitcoin.server.configuration;

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
    protected Boolean _transactionBloomFilterIsEnabled;
    protected Boolean _bootstrapIsEnabled;
    protected Boolean _trimBlocksIsEnabled;
    protected Boolean _blockCacheIsEnabled;
    protected Integer _maxMessagesPerSecond;
    protected String _dataDirectory;

    public Integer getBitcoinPort() { return _bitcoinPort; }
    public Integer getBitcoinRpcPort() { return _bitcoinRpcPort; }
    public SeedNodeProperties[] getSeedNodeProperties() { return Util.copyArray(_seedNodeProperties); }
    public SeedNodeProperties[] getWhitelistedNodes() { return Util.copyArray(_seedNodeProperties); }
    public Boolean isBanFilterEnabled() { return _banFilterIsEnabled; }
    public Integer getMaxPeerCount() { return _maxPeerCount; }
    public Integer getMaxThreadCount() { return _maxThreadCount; }
    public Long getTrustedBlockHeight() { return _trustedBlockHeight; }
    public Boolean skipNetworking() { return _shouldSkipNetworking; }
    public Long getMaxUtxoCacheByteCount() { return _maxUtxoCacheByteCount; }
    public Boolean isTransactionBloomFilterEnabled() { return _transactionBloomFilterIsEnabled; }
    public Boolean isTrimBlocksEnabled() { return _trimBlocksIsEnabled; }
    public Boolean isBlockCacheEnabled() { return _blockCacheIsEnabled; }
    public Integer getMaxMessagesPerSecond() { return _maxMessagesPerSecond; }
    public Boolean isBootstrapEnabled() { return _bootstrapIsEnabled; }
    public String getDataDirectory() { return _dataDirectory; }
}