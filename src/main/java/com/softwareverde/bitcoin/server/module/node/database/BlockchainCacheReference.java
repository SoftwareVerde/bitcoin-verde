package com.softwareverde.bitcoin.server.module.node.database;

import com.softwareverde.bitcoin.server.module.node.database.block.BlockchainCache;
import com.softwareverde.bitcoin.server.module.node.database.block.MutableBlockchainCache;
import com.softwareverde.database.DatabaseException;

public interface BlockchainCacheReference {
    BlockchainCache getBlockchainCache() throws DatabaseException;
    MutableBlockchainCache getMutableBlockchainCache() throws DatabaseException;
}
