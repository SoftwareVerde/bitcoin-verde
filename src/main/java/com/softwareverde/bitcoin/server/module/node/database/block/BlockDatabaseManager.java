package com.softwareverde.bitcoin.server.module.node.database.block;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.security.hash.sha256.Sha256Hash;

public interface BlockDatabaseManager {
    Object BLOCK_TRANSACTIONS_WRITE_MUTEX = new Object();

    BlockId getHeadBlockId() throws DatabaseException;
    Sha256Hash getHeadBlockHash() throws DatabaseException;

    Boolean hasTransactions(BlockId blockId) throws DatabaseException;
    Boolean hasTransactions(Sha256Hash blockHash) throws DatabaseException;
    Integer getTransactionCount(BlockId blockId) throws DatabaseException;
    List<TransactionId> getTransactionIds(BlockId blockId) throws DatabaseException;
}
