package com.softwareverde.bitcoin.server.module.node.database.fullnode;

import com.softwareverde.bitcoin.server.module.node.database.block.BlockchainCache;
import com.softwareverde.bitcoin.server.module.node.database.block.MutableBlockchainCache;

public class BlockchainCacheManager {
    protected MutableBlockchainCache _blockchainCache;

    public BlockchainCacheManager(final MutableBlockchainCache blockchainCache) {
        _blockchainCache = blockchainCache;
    }

    public BlockchainCacheManager(final int estimatedBlockCount) {
        _blockchainCache = new MutableBlockchainCache(estimatedBlockCount);
    }

    public BlockchainCache getBlockchainCache() {
        return _blockchainCache; // TODO: Needs to capture/lock-in the current version for requests that later grab a mutableCache... ...right?
    }

    public MutableBlockchainCache getNewBlockchainCache() {
        if (_blockchainCache == null) { return null; }

        _blockchainCache.pushVersion();
        return _blockchainCache;
    }

    public void rollbackBlockchainCache() {
        _blockchainCache.popVersion();
    }

    public void commitBlockchainCache() {
        _blockchainCache.applyVersion();
    }
}
