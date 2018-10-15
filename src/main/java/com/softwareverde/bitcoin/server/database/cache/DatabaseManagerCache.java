package com.softwareverde.bitcoin.server.database.cache;

import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.transaction.ImmutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.type.hash.sha256.ImmutableSha256Hash;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;

public interface DatabaseManagerCache {
    void log();
    void resetLog();

    // TRANSACTION ID CACHE --------------------------------------------------------------------------------------------
    void cacheTransactionId(ImmutableSha256Hash transactionHash, TransactionId transactionId);
    TransactionId getCachedTransactionId(ImmutableSha256Hash transactionHash);
    void invalidateTransactionIdCache();

    // TRANSACTION CACHE -----------------------------------------------------------------------------------------------
    void cacheTransaction(TransactionId transactionId, ImmutableTransaction transaction);
    Transaction getCachedTransaction(TransactionId transactionId);
    void invalidateTransactionCache();

    // TRANSACTION OUTPUT ID CACHE -------------------------------------------------------------------------------------
    void cacheTransactionOutputId(TransactionId transactionId, Integer transactionOutputIndex, TransactionOutputId transactionOutputId);
    TransactionOutputId getCachedTransactionOutputId(TransactionId transactionId, Integer transactionOutputIndex);
    void invalidateTransactionOutputIdCache();

    // BLOCK BLOCK CHAIN SEGMENT ID CACHE ------------------------------------------------------------------------------
    void cacheBlockChainSegmentId(BlockId blockId, BlockChainSegmentId blockChainSegmentId);
    BlockChainSegmentId getCachedBlockChainSegmentId(BlockId blockId);
    void invalidateBlockIdBlockChainSegmentIdCache();

    // ADDRESS ID CACHE ------------------------------------------------------------------------------------------------
    void cacheAddressId(String address, AddressId addressId);
    AddressId getCachedAddressId(String address);
    void invalidateAddressIdCache();

    // BLOCK HEADER CACHE ----------------------------------------------------------------------------------------------
//    void cacheBlockHeader(BlockHeader blockHeader);
//    BlockHeader getCachedBlockHeader(Sha256Hash blockHash);
//    BlockHeader getCachedBlockHeader(BlockId blockId);
    void cacheBlockHeight(BlockId blockId, Long blockHeight);
    Long getCachedBlockHeight(BlockId blockId);
    void invalidateBlockHeaderCache();
}
