package com.softwareverde.bitcoin.server.database.cache;

import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.transaction.ImmutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.type.hash.sha256.ImmutableSha256Hash;

public class EmptyDatabaseManagerCache implements DatabaseManagerCache {
    @Override
    public void log() { }

    @Override
    public void resetLog() { }

    @Override
    public void cacheTransactionId(final ImmutableSha256Hash transactionHash, final TransactionId transactionId) { }

    @Override
    public TransactionId getCachedTransactionId(final ImmutableSha256Hash transactionHash) { return null; }

    @Override
    public void invalidateTransactionIdCache() { }

    @Override
    public void cacheTransaction(final TransactionId transactionId, final ImmutableTransaction transaction) { }

    @Override
    public Transaction getCachedTransaction(final TransactionId transactionId) { return null; }

    @Override
    public void invalidateTransactionCache() { }

    @Override
    public void cacheTransactionOutputId(final TransactionId transactionId, final Integer transactionOutputIndex, final TransactionOutputId transactionOutputId) { }

    @Override
    public TransactionOutputId getCachedTransactionOutputId(final TransactionId transactionId, final Integer transactionOutputIndex) { return null; }

    @Override
    public void invalidateTransactionOutputIdCache() { }

    @Override
    public void cacheBlockChainSegmentId(final BlockId blockId, final BlockChainSegmentId blockChainSegmentId) { }

    @Override
    public BlockChainSegmentId getCachedBlockChainSegmentId(final BlockId blockId) { return null; }

    @Override
    public void invalidateBlockIdBlockChainSegmentIdCache() { }

    @Override
    public void cacheAddressId(final String address, final AddressId addressId) { }

    @Override
    public AddressId getCachedAddressId(final String address) { return null; }

    @Override
    public void invalidateAddressIdCache() { }
}
