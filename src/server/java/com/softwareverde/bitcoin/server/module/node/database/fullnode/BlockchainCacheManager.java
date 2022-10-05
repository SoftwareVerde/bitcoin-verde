package com.softwareverde.bitcoin.server.module.node.database.fullnode;

import com.softwareverde.bitcoin.server.module.node.database.block.MutableBlockchainCache;

public class BlockchainCacheManager {
    protected MutableBlockchainCache _blockchainCache;

    public BlockchainCacheManager() {
        this(null);
    }

    public BlockchainCacheManager(final MutableBlockchainCache blockchainCache) {
        _blockchainCache = blockchainCache;
    }

    public void initializeCache(final MutableBlockchainCache blockchainCache) {
        _blockchainCache = blockchainCache;
    }

    public MutableBlockchainCache getBlockchainCache() {
        if (_blockchainCache == null) { return null; }
        return _blockchainCache.newCopyOnWriteCache();
    }

    public void rollbackBlockchainCache() { }

    public void commitBlockchainCache(final MutableBlockchainCache blockchainCache) {
        if (_blockchainCache == null) { return; }
        if (blockchainCache == null) { return; }

        _blockchainCache.applyCache(blockchainCache);
    }
}
