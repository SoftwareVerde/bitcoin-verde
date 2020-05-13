package com.softwareverde.bitcoin.server.module.node.database.transaction;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.security.hash.sha256.Sha256Hash;

import java.util.Map;

public interface TransactionDatabaseManager {
    TransactionId getTransactionId(Sha256Hash transactionHash) throws DatabaseException;
    Sha256Hash getTransactionHash(TransactionId transactionId) throws DatabaseException;
    Map<Sha256Hash, TransactionId> getTransactionIds(List<Sha256Hash> transactionHashes) throws DatabaseException;
    Transaction getTransaction(TransactionId transactionId) throws DatabaseException;
    BlockId getBlockId(BlockchainSegmentId blockchainSegmentId, TransactionId transactionId) throws DatabaseException;
    Map<Sha256Hash, BlockId> getBlockIds(BlockchainSegmentId blockchainSegmentId, List<Sha256Hash> transactionHashes) throws DatabaseException;
    List<BlockId> getBlockIds(TransactionId transactionId) throws DatabaseException;
    List<BlockId> getBlockIds(Sha256Hash transactionHash) throws DatabaseException;
}
