package com.softwareverde.bitcoin.server.database.cache;

import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.transaction.ImmutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.type.hash.sha256.ImmutableSha256Hash;

public class ReadOnlyLocalDatabaseManagerCache implements DatabaseManagerCache {
    final MasterDatabaseManagerCache _masterDatabaseManagerCache;

    public ReadOnlyLocalDatabaseManagerCache(final MasterDatabaseManagerCache masterDatabaseManagerCache) {
        _masterDatabaseManagerCache = masterDatabaseManagerCache;
    }

    @Override
    public void log() { }

    @Override
    public void resetLog() { }

    @Override
    public void cacheTransactionId(final ImmutableSha256Hash transactionHash, final TransactionId transactionId) { }

    @Override
    public TransactionId getCachedTransactionId(final ImmutableSha256Hash transactionHash) {
        return _masterDatabaseManagerCache.getTransactionIdCache().getCachedItem(transactionHash);
    }

    @Override
    public void invalidateTransactionIdCache() { }

    @Override
    public void cacheTransaction(final TransactionId transactionId, final ImmutableTransaction transaction) { }

    public Transaction getCachedTransaction(final TransactionId transactionId) {
        return _masterDatabaseManagerCache.getTransactionCache().getCachedItem(transactionId);
    }

    @Override
    public void invalidateTransactionCache() { }

    @Override
    public void cacheTransactionOutputId(final TransactionId transactionId, final Integer transactionOutputIndex, final TransactionOutputId transactionOutputId) { }

    @Override
    public TransactionOutputId getCachedTransactionOutputId(final TransactionId transactionId, final Integer transactionOutputIndex) {
        return _masterDatabaseManagerCache.getTransactionOutputIdCache().getCachedItem(new CachedTransactionOutputIdentifier(transactionId, transactionOutputIndex));
    }

    @Override
    public void invalidateTransactionOutputIdCache() { }

    @Override
    public void cacheBlockChainSegmentId(final BlockId blockId, final BlockChainSegmentId blockChainSegmentId) { }

    @Override
    public BlockChainSegmentId getCachedBlockChainSegmentId(final BlockId blockId) {
        return _masterDatabaseManagerCache.getBlockIdBlockChainSegmentIdCache().getCachedItem(blockId);
    }

    @Override
    public void invalidateBlockIdBlockChainSegmentIdCache() { }

    public void cacheAddressId(final String address, final AddressId addressId) { }

    public AddressId getCachedAddressId(final String address) {
        return _masterDatabaseManagerCache.getAddressIdCache().getCachedItem(address);
    }

    @Override
    public void invalidateAddressIdCache() { }

    @Override
    public void cacheBlockHeight(final BlockId blockId, final Long blockHeight) {

    }

    @Override
    public Long getCachedBlockHeight(final BlockId blockId) {
        return null;
    }

    @Override
    public void invalidateBlockHeaderCache() {

    }

}
