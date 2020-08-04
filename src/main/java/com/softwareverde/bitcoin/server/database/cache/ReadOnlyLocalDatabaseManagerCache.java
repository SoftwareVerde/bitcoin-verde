package com.softwareverde.bitcoin.server.database.cache;

import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.server.database.cache.utxo.UnspentTransactionOutputCache;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public class ReadOnlyLocalDatabaseManagerCache implements DatabaseManagerCache {
    final MasterDatabaseManagerCache _masterDatabaseManagerCache;

    public ReadOnlyLocalDatabaseManagerCache(final MasterDatabaseManagerCache masterDatabaseManagerCache) {
        _masterDatabaseManagerCache = masterDatabaseManagerCache;
    }

    @Override
    public void cacheTransactionId(final Sha256Hash transactionHash, final TransactionId transactionId) {
        final Cache<Sha256Hash, TransactionId> transactionIdCache = _masterDatabaseManagerCache.getTransactionIdCache();
        transactionIdCache.invalidate(transactionHash);
    }

    @Override
    public void invalidateTransactionId(final Sha256Hash transactionHash) {
        final Cache<Sha256Hash, TransactionId> transactionIdCache = _masterDatabaseManagerCache.getTransactionIdCache();
        transactionIdCache.invalidate(transactionHash);
    }

    @Override
    public TransactionId getCachedTransactionId(final Sha256Hash transactionHash) {
        final Cache<Sha256Hash, TransactionId> transactionIdCache = _masterDatabaseManagerCache.getTransactionIdCache();
        return transactionIdCache.get(transactionHash);
    }

    @Override
    public void invalidateTransactionIdCache() {
        final Cache<Sha256Hash, TransactionId> transactionIdCache = _masterDatabaseManagerCache.getTransactionIdCache();
        transactionIdCache.invalidate();
    }

    @Override
    public void cacheTransaction(final TransactionId transactionId, final Transaction transaction) {
        final Cache<TransactionId, Transaction> transactionCache = _masterDatabaseManagerCache.getTransactionCache();
        transactionCache.invalidate(transactionId);
    }

    @Override
    public void invalidateTransaction(final TransactionId transactionId) {
        final Cache<TransactionId, Transaction> transactionCache = _masterDatabaseManagerCache.getTransactionCache();
        transactionCache.invalidate(transactionId);
    }

    @Override
    public Transaction getCachedTransaction(final TransactionId transactionId) {
        final Cache<TransactionId, Transaction> transactionCache = _masterDatabaseManagerCache.getTransactionCache();
        return transactionCache.get(transactionId);
    }

    @Override
    public void invalidateTransactionCache() {
        final Cache<TransactionId, Transaction> transactionCache = _masterDatabaseManagerCache.getTransactionCache();
        transactionCache.invalidate();
    }

    @Override
    public void cacheTransactionOutputId(final TransactionId transactionId, final Integer transactionOutputIndex, final TransactionOutputId transactionOutputId) {
        final CachedTransactionOutputIdentifier cachedTransactionOutputIdentifier = new CachedTransactionOutputIdentifier(transactionId, transactionOutputIndex);
        final Cache<CachedTransactionOutputIdentifier, TransactionOutputId> transactionCache = _masterDatabaseManagerCache.getTransactionOutputIdCache();
        transactionCache.invalidate(cachedTransactionOutputIdentifier);
    }

    @Override
    public void invalidateTransactionOutputId(final TransactionId transactionId, final Integer transactionOutputIndex) {
        final CachedTransactionOutputIdentifier cachedTransactionOutputIdentifier = new CachedTransactionOutputIdentifier(transactionId, transactionOutputIndex);
        final Cache<CachedTransactionOutputIdentifier, TransactionOutputId> transactionCache = _masterDatabaseManagerCache.getTransactionOutputIdCache();
        transactionCache.invalidate(cachedTransactionOutputIdentifier);
    }

    @Override
    public TransactionOutputId getCachedTransactionOutputId(final TransactionId transactionId, final Integer transactionOutputIndex) {
        final CachedTransactionOutputIdentifier cachedTransactionOutputIdentifier = new CachedTransactionOutputIdentifier(transactionId, transactionOutputIndex);
        final Cache<CachedTransactionOutputIdentifier, TransactionOutputId> transactionCache = _masterDatabaseManagerCache.getTransactionOutputIdCache();
        return transactionCache.get(cachedTransactionOutputIdentifier);
    }

    @Override
    public void invalidateTransactionOutputIdCache() {
        final Cache<CachedTransactionOutputIdentifier, TransactionOutputId> transactionCache = _masterDatabaseManagerCache.getTransactionOutputIdCache();
        transactionCache.invalidate();
    }

    @Override
    public void cacheUnspentTransactionOutputId(final Sha256Hash transactionHash, final Integer transactionOutputIndex, final TransactionOutputId transactionOutputId) {
        final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, transactionOutputIndex);
        final UnspentTransactionOutputCache unspentTransactionOutputIdCache = _masterDatabaseManagerCache.getUnspentTransactionOutputCache();
        unspentTransactionOutputIdCache.invalidateUnspentTransactionOutputId(transactionOutputIdentifier);
    }

    @Override
    public TransactionOutputId getCachedUnspentTransactionOutputId(final Sha256Hash transactionHash, final Integer transactionOutputIndex) {
        final UnspentTransactionOutputCache unspentTransactionOutputIdCache = _masterDatabaseManagerCache.getUnspentTransactionOutputCache();
        return unspentTransactionOutputIdCache.getCachedUnspentTransactionOutputId(transactionHash, transactionOutputIndex);
    }

    @Override
    public void invalidateUnspentTransactionOutputId(final TransactionOutputIdentifier transactionOutputIdentifier) {
        final UnspentTransactionOutputCache unspentTransactionOutputIdCache = _masterDatabaseManagerCache.getUnspentTransactionOutputCache();
        unspentTransactionOutputIdCache.invalidateUnspentTransactionOutputId(transactionOutputIdentifier);
    }

    @Override
    public void invalidateUnspentTransactionOutputIds(final List<TransactionOutputIdentifier> transactionOutputIdentifiers) {
        final UnspentTransactionOutputCache unspentTransactionOutputIdCache = _masterDatabaseManagerCache.getUnspentTransactionOutputCache();
        unspentTransactionOutputIdCache.invalidateUnspentTransactionOutputIds(transactionOutputIdentifiers);
    }

    @Override
    public void cacheBlockchainSegmentId(final BlockId blockId, final BlockchainSegmentId blockchainSegmentId) {
        final Cache<BlockId, BlockchainSegmentId> blockIdBlockchainSegmentIdCache = _masterDatabaseManagerCache.getBlockIdBlockchainSegmentIdCache();
        blockIdBlockchainSegmentIdCache.invalidate(blockId);
    }

    @Override
    public void invalidCachedBlockchainSegmentId(final BlockId blockId) {
        final Cache<BlockId, BlockchainSegmentId> blockIdBlockchainSegmentIdCache = _masterDatabaseManagerCache.getBlockIdBlockchainSegmentIdCache();
        blockIdBlockchainSegmentIdCache.invalidate(blockId);
    }

    @Override
    public BlockchainSegmentId getCachedBlockchainSegmentId(final BlockId blockId) {
        final Cache<BlockId, BlockchainSegmentId> blockIdBlockchainSegmentIdCache = _masterDatabaseManagerCache.getBlockIdBlockchainSegmentIdCache();
        return blockIdBlockchainSegmentIdCache.get(blockId);
    }

    @Override
    public void invalidateBlockIdBlockchainSegmentIdCache() {
        final Cache<BlockId, BlockchainSegmentId> blockIdBlockchainSegmentIdCache = _masterDatabaseManagerCache.getBlockIdBlockchainSegmentIdCache();
        blockIdBlockchainSegmentIdCache.invalidate();
    }

    @Override
    public void cacheAddressId(final String address, final AddressId addressId) {
        final Cache<String, AddressId> addressIdCache = _masterDatabaseManagerCache.getAddressIdCache();
        addressIdCache.invalidate(address);
    }

    @Override
    public void invalidateAddressId(final String address) {
        final Cache<String, AddressId> addressIdCache = _masterDatabaseManagerCache.getAddressIdCache();
        addressIdCache.invalidate(address);
    }

    @Override
    public AddressId getCachedAddressId(final String address) {
        final Cache<String, AddressId> addressIdCache = _masterDatabaseManagerCache.getAddressIdCache();
        return addressIdCache.get(address);
    }

    @Override
    public void invalidateAddressIdCache() {
        final Cache<String, AddressId> addressIdCache = _masterDatabaseManagerCache.getAddressIdCache();
        addressIdCache.invalidate();
    }

    @Override
    public void cacheBlockHeight(final BlockId blockId, final Long blockHeight) {
        final Cache<BlockId, Long> blockHeightCache = _masterDatabaseManagerCache.getBlockHeightCache();
        blockHeightCache.invalidate(blockId);
    }

    @Override
    public void invalidateBlockHeight(final BlockId blockId) {
        final Cache<BlockId, Long> blockHeightCache = _masterDatabaseManagerCache.getBlockHeightCache();
        blockHeightCache.invalidate(blockId);
    }

    @Override
    public Long getCachedBlockHeight(final BlockId blockId) {
        final Cache<BlockId, Long> blockHeightCache = _masterDatabaseManagerCache.getBlockHeightCache();
        return blockHeightCache.get(blockId);
    }

    @Override
    public void invalidateBlockHeaderCache() {
        final Cache<BlockId, Long> blockHeightCache = _masterDatabaseManagerCache.getBlockHeightCache();
        blockHeightCache.invalidate();
    }

    @Override
    public void close() {
        // Nothing.
    }

}
