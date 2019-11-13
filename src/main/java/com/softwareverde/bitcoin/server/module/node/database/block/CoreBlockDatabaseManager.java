/*
package com.softwareverde.bitcoin.server.module.node.database.block;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.database.DatabaseException;

public interface CoreBlockDatabaseManager extends BlockDatabaseManager {
    BlockId insertBlock(Block block) throws DatabaseException;
    BlockId storeBlock(Block block) throws DatabaseException;
    Boolean storeBlockTransactions(Block block) throws DatabaseException;

    MutableBlock getBlock(BlockId blockId) throws DatabaseException;
    MutableBlock getBlock(BlockId blockId, Boolean shouldUpdateUnspentOutputCache) throws DatabaseException;

    void repairBlock(Block block) throws DatabaseException;
}
*/