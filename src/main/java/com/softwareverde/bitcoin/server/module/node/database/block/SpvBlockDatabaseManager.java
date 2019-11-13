/*
package com.softwareverde.bitcoin.server.module.node.database.block;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.merkleroot.PartialMerkleTree;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;

public interface SpvBlockDatabaseManager extends BlockDatabaseManager {
    List<BlockId> getBlockIdsWithTransactions() throws DatabaseException;
    void storePartialMerkleTree(BlockId blockId, PartialMerkleTree partialMerkleTree) throws DatabaseException;
    BlockId selectNextIncompleteBlock(Long minBlockHeight) throws DatabaseException;
    void addTransactionToBlock(BlockId blockId, TransactionId transactionId) throws DatabaseException;
}
*/