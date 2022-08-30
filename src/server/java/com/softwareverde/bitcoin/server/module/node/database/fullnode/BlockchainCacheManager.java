package com.softwareverde.bitcoin.server.module.node.database.fullnode;

import com.softwareverde.bitcoin.server.module.node.database.block.BlockchainCache;
import com.softwareverde.bitcoin.server.module.node.database.block.MutableBlockchainCache;

public class BlockchainCacheManager {
    protected BlockchainCache _blockchainCache;

    public BlockchainCacheManager(final MutableBlockchainCache blockchainCache) {
        _blockchainCache = blockchainCache;
    }

    public BlockchainCacheManager(final int estimatedBlockCount) {
        _blockchainCache = new MutableBlockchainCache(estimatedBlockCount);
    }

    public BlockchainCache getBlockchainCache() {
        return _blockchainCache;
    }

    public MutableBlockchainCache getNewBlockchainCache() {
        if (_blockchainCache == null) { return null; }

        return _blockchainCache.copy();
    }

    public void commitBlockchainCache(final BlockchainCache blockchainCache) {
        _blockchainCache = blockchainCache;
    }
}
