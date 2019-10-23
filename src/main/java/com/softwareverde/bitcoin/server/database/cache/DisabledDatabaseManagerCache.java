package com.softwareverde.bitcoin.server.database.cache;

import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.hash.sha256.ImmutableSha256Hash;
import com.softwareverde.bitcoin.transaction.ConstTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;

public class DisabledDatabaseManagerCache implements DatabaseManagerCache {
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
    public void cacheTransaction(final TransactionId transactionId, final ConstTransaction transaction) { }

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
    public void cacheBlockchainSegmentId(final BlockId blockId, final BlockchainSegmentId blockchainSegmentId) { }

    @Override
    public BlockchainSegmentId getCachedBlockchainSegmentId(final BlockId blockId) { return null; }

    @Override
    public void invalidateBlockIdBlockchainSegmentIdCache() { }

    @Override
    public void cacheAddressId(final String address, final AddressId addressId) { }

    @Override
    public AddressId getCachedAddressId(final String address) { return null; }

    @Override
    public void invalidateAddressIdCache() { }

    @Override
    public void cacheBlockHeight(final BlockId blockId, final Long blockHeight) { }

    @Override
    public Long getCachedBlockHeight(final BlockId blockId) { return null; }

    @Override
    public void invalidateBlockHeaderCache() { }

    @Override
    public void close() { }
}
