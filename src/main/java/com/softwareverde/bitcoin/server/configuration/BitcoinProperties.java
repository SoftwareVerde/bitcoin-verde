package com.softwareverde.bitcoin.server.configuration;

import com.softwareverde.util.Util;

public class BitcoinProperties {
    public static final Integer PORT = 8333;
    public static final Integer RPC_PORT = 8334;

    protected Integer _bitcoinPort;
    protected Integer _bitcoinRpcPort;
    protected SeedNodeProperties[] _seedNodeProperties;
    protected Integer _maxPeerCount;
    protected Integer _maxThreadCount;
    protected Long _trustedBlockHeight;
    protected Boolean _shouldSkipNetworking;
    protected Long _maxUtxoCacheByteCount;
    protected Boolean _transactionBloomFilterIsEnabled;
    protected Boolean _bootstrapIsEnabled;
    protected Boolean _shouldTrimBlocks;
    protected Integer _maxMessagesPerSecond;
    protected String _dataDirectory;

    public Integer getBitcoinPort() { return _bitcoinPort; }
    public Integer getBitcoinRpcPort() { return _bitcoinRpcPort; }
    public SeedNodeProperties[] getSeedNodeProperties() { return Util.copyArray(_seedNodeProperties); }
    public Integer getMaxPeerCount() { return _maxPeerCount; }
    public Integer getMaxThreadCount() { return _maxThreadCount; }
    public Long getTrustedBlockHeight() { return _trustedBlockHeight; }
    public Boolean skipNetworking() { return _shouldSkipNetworking; }
    public Long getMaxUtxoCacheByteCount() { return _maxUtxoCacheByteCount; }
    public Boolean isTransactionBloomFilterEnabled() { return _transactionBloomFilterIsEnabled; }
    public Boolean shouldTrimBlocks() { return _shouldTrimBlocks; }
    public Integer getMaxMessagesPerSecond() { return _maxMessagesPerSecond; }
    public Boolean isBootstrapEnabled() { return _bootstrapIsEnabled; }
    public String getDataDirectory() { return _dataDirectory; }
}