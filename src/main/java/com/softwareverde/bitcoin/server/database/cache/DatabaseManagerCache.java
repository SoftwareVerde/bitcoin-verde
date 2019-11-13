package com.softwareverde.bitcoin.server.database.cache;

import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.hash.sha256.ImmutableSha256Hash;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.transaction.ConstTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.list.List;

public interface DatabaseManagerCache extends AutoCloseable {
    void log();
    void resetLog();

    // TRANSACTION ID CACHE --------------------------------------------------------------------------------------------
    void cacheTransactionId(ImmutableSha256Hash transactionHash, TransactionId transactionId);
    TransactionId getCachedTransactionId(ImmutableSha256Hash transactionHash);
    void invalidateTransactionIdCache();

    // TRANSACTION CACHE -----------------------------------------------------------------------------------------------
    void cacheTransaction(TransactionId transactionId, ConstTransaction transaction);
    Transaction getCachedTransaction(TransactionId transactionId);
    void invalidateTransactionCache();

    // TRANSACTION OUTPUT ID CACHE -------------------------------------------------------------------------------------
    void cacheTransactionOutputId(TransactionId transactionId, Integer transactionOutputIndex, TransactionOutputId transactionOutputId);
    TransactionOutputId getCachedTransactionOutputId(TransactionId transactionId, Integer transactionOutputIndex);
    void invalidateTransactionOutputIdCache();

    // UNSPENT TRANSACTION OUTPUT ID CACHE -----------------------------------------------------------------------------
    void cacheUnspentTransactionOutputId(Sha256Hash transactionHash, Integer transactionOutputIndex, TransactionOutputId transactionOutputId);
    TransactionOutputId getCachedUnspentTransactionOutputId(Sha256Hash transactionHash, Integer transactionOutputIndex);
    void invalidateUnspentTransactionOutputId(TransactionOutputIdentifier transactionOutputId);
    void invalidateUnspentTransactionOutputIds(List<TransactionOutputIdentifier> transactionOutputIds);

    // BLOCK BLOCK CHAIN SEGMENT ID CACHE ------------------------------------------------------------------------------
    void cacheBlockchainSegmentId(BlockId blockId, BlockchainSegmentId blockchainSegmentId);
    BlockchainSegmentId getCachedBlockchainSegmentId(BlockId blockId);
    void invalidateBlockIdBlockchainSegmentIdCache();

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

    @Override
    void close();
}
