package com.softwareverde.bitcoin.server.database.cache;

import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;

public interface DatabaseManagerCache extends AutoCloseable {
    // TRANSACTION ID CACHE --------------------------------------------------------------------------------------------
    TransactionId getCachedTransactionId(Sha256Hash transactionHash);
    void cacheTransactionId(Sha256Hash transactionHash, TransactionId transactionId);
    void invalidateTransactionId(Sha256Hash transactionHash);
    void invalidateTransactionIdCache();

    // TRANSACTION CACHE -----------------------------------------------------------------------------------------------
    Transaction getCachedTransaction(TransactionId transactionId);
    void cacheTransaction(TransactionId transactionId, Transaction transaction);
    void invalidateTransaction(TransactionId transactionId);
    void invalidateTransactionCache();

    // TRANSACTION OUTPUT ID CACHE -------------------------------------------------------------------------------------
    TransactionOutputId getCachedTransactionOutputId(TransactionId transactionId, Integer transactionOutputIndex);
    void cacheTransactionOutputId(TransactionId transactionId, Integer transactionOutputIndex, TransactionOutputId transactionOutputId);
    void invalidateTransactionOutputId(TransactionId transactionId, Integer transactionOutputIndex);
    void invalidateTransactionOutputIdCache();

    // BLOCK BLOCK CHAIN SEGMENT ID CACHE ------------------------------------------------------------------------------
    BlockchainSegmentId getCachedBlockchainSegmentId(BlockId blockId);
    void cacheBlockchainSegmentId(BlockId blockId, BlockchainSegmentId blockchainSegmentId);
    void invalidCachedBlockchainSegmentId(BlockId blockId);
    void invalidateBlockIdBlockchainSegmentIdCache();

    // ADDRESS ID CACHE ------------------------------------------------------------------------------------------------
    AddressId getCachedAddressId(String address);
    void cacheAddressId(String address, AddressId addressId);
    void invalidateAddressId(String address);
    void invalidateAddressIdCache();

    // BLOCK HEADER CACHE ----------------------------------------------------------------------------------------------
    Long getCachedBlockHeight(BlockId blockId);
    void cacheBlockHeight(BlockId blockId, Long blockHeight);
    void invalidateBlockHeight(BlockId blockId);
    void invalidateBlockHeaderCache();

    @Override
    void close();
}
