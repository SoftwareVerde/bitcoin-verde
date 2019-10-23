package com.softwareverde.bitcoin.server.database.cache;

import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.hash.sha256.ImmutableSha256Hash;
import com.softwareverde.bitcoin.transaction.ConstTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;

public class LocalDatabaseManagerCache implements DatabaseManagerCache {

    public LocalDatabaseManagerCache() { }

    public LocalDatabaseManagerCache(final MasterDatabaseManagerCache masterCache) {
        _transactionIdCache.setMasterCache(masterCache.getTransactionIdCache());
        _transactionCache.setMasterCache(masterCache.getTransactionCache());
        _transactionOutputIdCache.setMasterCache(masterCache.getTransactionOutputIdCache());
        _blockIdBlockchainSegmentIdCache.setMasterCache(masterCache.getBlockIdBlockchainSegmentIdCache());
        _addressIdCache.setMasterCache(masterCache.getAddressIdCache());
        _blockHeightCache.setMasterCache(masterCache.getBlockHeightCache());
    }

    @Override
    public void log() {
        _transactionIdCache.debug();
        _transactionCache.debug();
        _transactionOutputIdCache.debug();
        _blockIdBlockchainSegmentIdCache.debug();
        _addressIdCache.debug();
    }

    @Override
    public void resetLog() {
        _transactionIdCache.resetDebug();
        _transactionCache.resetDebug();
        _transactionOutputIdCache.resetDebug();
        _blockIdBlockchainSegmentIdCache.resetDebug();
        _addressIdCache.resetDebug();
    }


    // TRANSACTION ID CACHE --------------------------------------------------------------------------------------------

    protected final HashMapCache<ImmutableSha256Hash, TransactionId> _transactionIdCache = new HashMapCache<ImmutableSha256Hash, TransactionId>("TransactionIdCache", HashMapCache.DEFAULT_CACHE_SIZE);

    @Override
    public void cacheTransactionId(final ImmutableSha256Hash transactionHash, final TransactionId transactionId) {
        _transactionIdCache.cacheItem(transactionHash, transactionId);
    }

    @Override
    public TransactionId getCachedTransactionId(final ImmutableSha256Hash transactionHash) {
        return _transactionIdCache.getCachedItem(transactionHash);
    }

    @Override
    public void invalidateTransactionIdCache() {
        _transactionIdCache.invalidate();
    }

    public HashMapCache<ImmutableSha256Hash, TransactionId> getTransactionIdCache() { return _transactionIdCache; }

    // -----------------------------------------------------------------------------------------------------------------


    // TRANSACTION CACHE -----------------------------------------------------------------------------------------------

    protected final HashMapCache<TransactionId, ConstTransaction> _transactionCache = new HashMapCache<TransactionId, ConstTransaction>("TransactionCache", HashMapCache.DEFAULT_CACHE_SIZE);

    @Override
    public void cacheTransaction(final TransactionId transactionId, final ConstTransaction transaction) {
        _transactionCache.cacheItem(transactionId, transaction);
    }

    @Override
    public Transaction getCachedTransaction(final TransactionId transactionId) {
        return _transactionCache.getCachedItem(transactionId);
    }

    @Override
    public void invalidateTransactionCache() {
        _transactionCache.invalidate();
    }

    public HashMapCache<TransactionId, ConstTransaction> getTransactionCache() { return _transactionCache; }

    // -----------------------------------------------------------------------------------------------------------------

    // TRANSACTION OUTPUT ID CACHE -------------------------------------------------------------------------------------

    protected final HashMapCache<CachedTransactionOutputIdentifier, TransactionOutputId> _transactionOutputIdCache = new HashMapCache<CachedTransactionOutputIdentifier, TransactionOutputId>("TransactionOutputId", 1048576);

    @Override
    public void cacheTransactionOutputId(final TransactionId transactionId, final Integer transactionOutputIndex, final TransactionOutputId transactionOutputId) {
        final CachedTransactionOutputIdentifier cachedTransactionOutputIdentifier = new CachedTransactionOutputIdentifier(transactionId, transactionOutputIndex);
        _transactionOutputIdCache.cacheItem(cachedTransactionOutputIdentifier, transactionOutputId);
    }

    @Override
    public TransactionOutputId getCachedTransactionOutputId(final TransactionId transactionId, final Integer transactionOutputIndex) {
        final CachedTransactionOutputIdentifier cachedTransactionOutputIdentifier = new CachedTransactionOutputIdentifier(transactionId, transactionOutputIndex);
        return _transactionOutputIdCache.getCachedItem(cachedTransactionOutputIdentifier);
    }

    @Override
    public void invalidateTransactionOutputIdCache() {
        _transactionOutputIdCache.invalidate();
    }

    public HashMapCache<CachedTransactionOutputIdentifier, TransactionOutputId> getTransactionOutputIdCache() { return _transactionOutputIdCache; }

    // -----------------------------------------------------------------------------------------------------------------

    // BLOCK BLOCK CHAIN SEGMENT ID CACHE ------------------------------------------------------------------------------

    protected final HashMapCache<BlockId, BlockchainSegmentId> _blockIdBlockchainSegmentIdCache = new HashMapCache<BlockId, BlockchainSegmentId>("BlockId-BlockchainSegmentId", 1460);

    @Override
    public void cacheBlockchainSegmentId(final BlockId blockId, final BlockchainSegmentId blockchainSegmentId) {
        _blockIdBlockchainSegmentIdCache.cacheItem(blockId, blockchainSegmentId);
    }

    @Override
    public BlockchainSegmentId getCachedBlockchainSegmentId(final BlockId blockId) {
        return _blockIdBlockchainSegmentIdCache.getCachedItem(blockId);
    }

    @Override
    public void invalidateBlockIdBlockchainSegmentIdCache() {
        _blockIdBlockchainSegmentIdCache.invalidate();
    }

    public HashMapCache<BlockId, BlockchainSegmentId> getBlockIdBlockchainSegmentIdCache() { return _blockIdBlockchainSegmentIdCache; }

    // -----------------------------------------------------------------------------------------------------------------

    // ADDRESS ID CACHE ------------------------------------------------------------------------------------------------

    protected final HashMapCache<String, AddressId> _addressIdCache = new HashMapCache<String, AddressId>("AddressId", HashMapCache.DISABLED_CACHE_SIZE);

    @Override
    public void cacheAddressId(final String address, final AddressId addressId) {
        _addressIdCache.cacheItem(address, addressId);
    }

    @Override
    public AddressId getCachedAddressId(final String address) {
        return _addressIdCache.getCachedItem(address);
    }

    @Override
    public void invalidateAddressIdCache() {
        _addressIdCache.invalidate();
    }

    public HashMapCache<String, AddressId> getAddressIdCache() { return _addressIdCache; }

    // -----------------------------------------------------------------------------------------------------------------

    // BLOCK HEIGHT CACHE ----------------------------------------------------------------------------------------------

    protected final HashMapCache<BlockId, Long> _blockHeightCache = new HashMapCache<BlockId, Long>("BlockHeight", 500000);

    @Override
    public void cacheBlockHeight(final BlockId blockId, final Long blockHeight) {
        _blockHeightCache.cacheItem(blockId, blockHeight);
    }

    @Override
    public Long getCachedBlockHeight(final BlockId blockId) {
        return _blockHeightCache.getCachedItem(blockId);
    }

    @Override
    public void invalidateBlockHeaderCache() {
        _blockHeightCache.invalidate();
    }

    public HashMapCache<BlockId, Long> getBlockHeightCache() { return _blockHeightCache; }

    // -----------------------------------------------------------------------------------------------------------------

    @Override
    public void close() {
        // Nothing.
    }

}
