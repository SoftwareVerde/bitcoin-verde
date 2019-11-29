package com.softwareverde.bitcoin.server.database.cache;

import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.database.cache.utxo.DisabledUnspentTransactionOutputCache;
import com.softwareverde.bitcoin.server.database.cache.utxo.UnspentTransactionOutputCache;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.list.List;
import com.softwareverde.util.Util;

public class LocalDatabaseManagerCache implements DatabaseManagerCache {

    // TODO: Cache sizes should be reduced for intermediary caches...
    protected final HashMapCache<Sha256Hash, TransactionId> _transactionIdCache = new HashMapCache<Sha256Hash, TransactionId>("TransactionIdCache", HashMapCache.DEFAULT_CACHE_SIZE);
    protected final HashMapCache<TransactionId, Transaction> _transactionCache = new HashMapCache<TransactionId, Transaction>("TransactionCache", HashMapCache.DEFAULT_CACHE_SIZE);
    protected final HashMapCache<CachedTransactionOutputIdentifier, TransactionOutputId> _transactionOutputIdCache = new HashMapCache<CachedTransactionOutputIdentifier, TransactionOutputId>("TransactionOutputId", 1048576);
    protected final HashMapCache<BlockId, BlockchainSegmentId> _blockIdBlockchainSegmentIdCache = new HashMapCache<BlockId, BlockchainSegmentId>("BlockId-BlockchainSegmentId", 1460);
    protected final HashMapCache<String, AddressId> _addressIdCache = new HashMapCache<String, AddressId>("AddressId", HashMapCache.DISABLED_CACHE_SIZE);
    protected final HashMapCache<BlockId, Long> _blockHeightCache = new HashMapCache<BlockId, Long>("BlockHeight", 500000);
    protected final UnspentTransactionOutputCache _unspentTransactionOutputCache;

    public LocalDatabaseManagerCache() {
        _unspentTransactionOutputCache = new DisabledUnspentTransactionOutputCache();
    }

    public LocalDatabaseManagerCache(final UnspentTransactionOutputCache unspentTransactionOutputCache) {
        _unspentTransactionOutputCache = Util.coalesce(unspentTransactionOutputCache, new DisabledUnspentTransactionOutputCache());
    }

    public LocalDatabaseManagerCache(final MasterDatabaseManagerCache masterCache) {
        _unspentTransactionOutputCache = masterCache.newUnspentTransactionOutputCache();

        _transactionIdCache.setMasterCache(masterCache.getTransactionIdCache());
        _transactionCache.setMasterCache(masterCache.getTransactionCache());
        _transactionOutputIdCache.setMasterCache(masterCache.getTransactionOutputIdCache());
        _blockIdBlockchainSegmentIdCache.setMasterCache(masterCache.getBlockIdBlockchainSegmentIdCache());
        _addressIdCache.setMasterCache(masterCache.getAddressIdCache());
        _blockHeightCache.setMasterCache(masterCache.getBlockHeightCache());
        _unspentTransactionOutputCache.setMasterCache(masterCache.getUnspentTransactionOutputCache());
    }

    // TRANSACTION ID CACHE --------------------------------------------------------------------------------------------

    @Override
    public void cacheTransactionId(final Sha256Hash transactionHash, final TransactionId transactionId) {
        _transactionIdCache.set(transactionHash.asConst(), transactionId);
    }

    @Override
    public void invalidateTransactionId(final Sha256Hash transactionHash) {
        _transactionIdCache.invalidate(transactionHash);
    }

    @Override
    public TransactionId getCachedTransactionId(final Sha256Hash transactionHash) {
        return _transactionIdCache.get(transactionHash);
    }

    @Override
    public void invalidateTransactionIdCache() {
        _transactionIdCache.invalidate();
    }

    public HashMapCache<Sha256Hash, TransactionId> getTransactionIdCache() { return _transactionIdCache; }

    // -----------------------------------------------------------------------------------------------------------------


    // TRANSACTION CACHE -----------------------------------------------------------------------------------------------

    @Override
    public void cacheTransaction(final TransactionId transactionId, final Transaction transaction) {
        _transactionCache.set(transactionId, transaction);
    }

    @Override
    public void invalidateTransaction(final TransactionId transactionId) {
        _transactionCache.invalidate(transactionId);
    }

    @Override
    public Transaction getCachedTransaction(final TransactionId transactionId) {
        return _transactionCache.get(transactionId);
    }

    @Override
    public void invalidateTransactionCache() {
        _transactionCache.invalidate();
    }

    public HashMapCache<TransactionId, Transaction> getTransactionCache() { return _transactionCache; }

    // -----------------------------------------------------------------------------------------------------------------

    // TRANSACTION OUTPUT ID CACHE -------------------------------------------------------------------------------------

    @Override
    public void cacheTransactionOutputId(final TransactionId transactionId, final Integer transactionOutputIndex, final TransactionOutputId transactionOutputId) {
        final CachedTransactionOutputIdentifier cachedTransactionOutputIdentifier = new CachedTransactionOutputIdentifier(transactionId, transactionOutputIndex);
        _transactionOutputIdCache.set(cachedTransactionOutputIdentifier, transactionOutputId);
    }

    @Override
    public void invalidateTransactionOutputId(final TransactionId transactionId, final Integer transactionOutputIndex) {
        final CachedTransactionOutputIdentifier cachedTransactionOutputIdentifier = new CachedTransactionOutputIdentifier(transactionId, transactionOutputIndex);
        _transactionOutputIdCache.invalidate(cachedTransactionOutputIdentifier);
    }

    @Override
    public TransactionOutputId getCachedTransactionOutputId(final TransactionId transactionId, final Integer transactionOutputIndex) {
        final CachedTransactionOutputIdentifier cachedTransactionOutputIdentifier = new CachedTransactionOutputIdentifier(transactionId, transactionOutputIndex);
        return _transactionOutputIdCache.get(cachedTransactionOutputIdentifier);
    }

    @Override
    public void invalidateTransactionOutputIdCache() {
        _transactionOutputIdCache.invalidate();
    }

    public HashMapCache<CachedTransactionOutputIdentifier, TransactionOutputId> getTransactionOutputIdCache() { return _transactionOutputIdCache; }

    // -----------------------------------------------------------------------------------------------------------------

    // BLOCK BLOCK CHAIN SEGMENT ID CACHE ------------------------------------------------------------------------------

    @Override
    public void cacheBlockchainSegmentId(final BlockId blockId, final BlockchainSegmentId blockchainSegmentId) {
        _blockIdBlockchainSegmentIdCache.set(blockId, blockchainSegmentId);
    }

    @Override
    public void invalidCachedBlockchainSegmentId(final BlockId blockId) {
        _blockIdBlockchainSegmentIdCache.invalidate(blockId);
    }

    @Override
    public BlockchainSegmentId getCachedBlockchainSegmentId(final BlockId blockId) {
        return _blockIdBlockchainSegmentIdCache.get(blockId);
    }

    @Override
    public void invalidateBlockIdBlockchainSegmentIdCache() {
        _blockIdBlockchainSegmentIdCache.invalidate();
    }

    public HashMapCache<BlockId, BlockchainSegmentId> getBlockIdBlockchainSegmentIdCache() { return _blockIdBlockchainSegmentIdCache; }

    // -----------------------------------------------------------------------------------------------------------------

    // ADDRESS ID CACHE ------------------------------------------------------------------------------------------------

    @Override
    public void cacheAddressId(final String address, final AddressId addressId) {
        _addressIdCache.set(address, addressId);
    }

    @Override
    public void invalidateAddressId(final String address) {
        _addressIdCache.invalidate(address);
    }

    @Override
    public AddressId getCachedAddressId(final String address) {
        return _addressIdCache.get(address);
    }

    @Override
    public void invalidateAddressIdCache() {
        _addressIdCache.invalidate();
    }

    public HashMapCache<String, AddressId> getAddressIdCache() { return _addressIdCache; }

    // -----------------------------------------------------------------------------------------------------------------

    // BLOCK HEIGHT CACHE ----------------------------------------------------------------------------------------------

    @Override
    public void cacheBlockHeight(final BlockId blockId, final Long blockHeight) {
        _blockHeightCache.set(blockId, blockHeight);
    }

    @Override
    public void invalidateBlockHeight(final BlockId blockId) {
        _blockHeightCache.invalidate(blockId);
    }

    @Override
    public Long getCachedBlockHeight(final BlockId blockId) {
        return _blockHeightCache.get(blockId);
    }

    @Override
    public void invalidateBlockHeaderCache() {
        _blockHeightCache.invalidate();
    }

    public HashMapCache<BlockId, Long> getBlockHeightCache() { return _blockHeightCache; }

    // -----------------------------------------------------------------------------------------------------------------

    // UNSPENT TRANSACTION OUTPUT CACHE --------------------------------------------------------------------------------

    @Override
    public void cacheUnspentTransactionOutputId(final Sha256Hash transactionHash, final Integer transactionOutputIndex, final TransactionOutputId transactionOutputId) {
        _unspentTransactionOutputCache.cacheUnspentTransactionOutputId(transactionHash, transactionOutputIndex, transactionOutputId);
    }

    @Override
    public TransactionOutputId getCachedUnspentTransactionOutputId(final Sha256Hash transactionHash, final Integer transactionOutputIndex) {
        return _unspentTransactionOutputCache.getCachedUnspentTransactionOutputId(transactionHash, transactionOutputIndex);
    }

    @Override
    public void invalidateUnspentTransactionOutputId(final TransactionOutputIdentifier transactionOutputId) {
        _unspentTransactionOutputCache.invalidateUnspentTransactionOutputId(transactionOutputId);
    }

    @Override
    public void invalidateUnspentTransactionOutputIds(final List<TransactionOutputIdentifier> transactionOutputIds) {
        _unspentTransactionOutputCache.invalidateUnspentTransactionOutputIds(transactionOutputIds);
    }

    public UnspentTransactionOutputCache getUnspentTransactionOutputCache() { return _unspentTransactionOutputCache; }

    // -----------------------------------------------------------------------------------------------------------------

    @Override
    public void close() {
        _unspentTransactionOutputCache.close();
    }

}
