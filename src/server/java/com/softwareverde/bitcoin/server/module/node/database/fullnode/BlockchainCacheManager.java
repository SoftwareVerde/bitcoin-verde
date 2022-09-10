package com.softwareverde.bitcoin.server.module.node.database.fullnode;

import com.softwareverde.bitcoin.server.module.node.database.block.BlockchainCache;
import com.softwareverde.bitcoin.server.module.node.database.block.MutableBlockchainCache;
import com.softwareverde.bitcoin.server.module.node.database.block.ReadOnlyBlockchainCache;

public class BlockchainCacheManager {
    protected MutableBlockchainCache _blockchainCache;

    public BlockchainCacheManager() {
        this(null);
    }

    public BlockchainCacheManager(final MutableBlockchainCache blockchainCache) {
        _blockchainCache = blockchainCache;
    }

    public void initializeCache(final int estimatedBlockCount) {
        _blockchainCache = new MutableBlockchainCache(estimatedBlockCount);
    }

    public BlockchainCache getBlockchainCache() {
        return _blockchainCache;
    }

    public MutableBlockchainCache getNewBlockchainCache() {
        if (_blockchainCache == null) { return null; }

        _blockchainCache.pushVersion();
        return _blockchainCache;
    }

    public void rollbackBlockchainCache() {
        if (_blockchainCache == null) { return; }

        _blockchainCache.popVersion();
    }

    public void commitBlockchainCache() {
        if (_blockchainCache == null) { return; }

        _blockchainCache.applyVersion();
    }
}
