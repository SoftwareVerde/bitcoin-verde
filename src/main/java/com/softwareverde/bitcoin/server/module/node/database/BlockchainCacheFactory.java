package com.softwareverde.bitcoin.server.module.node.database;

import com.softwareverde.bitcoin.server.module.node.database.block.BlockchainCache;
import com.softwareverde.bitcoin.server.module.node.database.block.MutableBlockchainCache;

public interface BlockchainCacheFactory {
    BlockchainCache getBlockchainCache();
    MutableBlockchainCache getMutableBlockchainCache();
}
