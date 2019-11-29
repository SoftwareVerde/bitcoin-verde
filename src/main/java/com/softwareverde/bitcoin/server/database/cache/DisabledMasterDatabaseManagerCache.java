package com.softwareverde.bitcoin.server.database.cache;

import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.hash.sha256.ImmutableSha256Hash;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.database.cache.utxo.DisabledUnspentTransactionOutputCache;
import com.softwareverde.bitcoin.server.database.cache.utxo.UnspentTransactionOutputCache;
import com.softwareverde.bitcoin.server.database.cache.utxo.UtxoCount;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;

public class DisabledMasterDatabaseManagerCache implements MasterDatabaseManagerCache {
    protected final Cache<TransactionId, Transaction> _transactionCache = new DisabledCache<>();
    protected final Cache<Sha256Hash, TransactionId> _transactionIdCache = new DisabledCache<>();
    protected final Cache<CachedTransactionOutputIdentifier, TransactionOutputId> _transactionOutputIdCache = new DisabledCache<>();
    protected final Cache<BlockId, BlockchainSegmentId> _blockIdBlockchainSegmentIdCache = new DisabledCache<>();
    protected final Cache<String, AddressId> _addressIdCache = new DisabledCache<>();
    protected final Cache<BlockId, Long> _blockHeightCache = new DisabledCache<>();
    protected final UnspentTransactionOutputCache _unspentTransactionOutputCache = new DisabledUnspentTransactionOutputCache();

    @Override
    public Cache<TransactionId, Transaction> getTransactionCache() {
        return _transactionCache;
    }

    @Override
    public Cache<Sha256Hash, TransactionId> getTransactionIdCache() {
        return _transactionIdCache;
    }

    @Override
    public Cache<CachedTransactionOutputIdentifier, TransactionOutputId> getTransactionOutputIdCache() {
        return _transactionOutputIdCache;
    }

    @Override
    public Cache<BlockId, BlockchainSegmentId> getBlockIdBlockchainSegmentIdCache() {
        return _blockIdBlockchainSegmentIdCache;
    }

    @Override
    public Cache<String, AddressId> getAddressIdCache() {
        return _addressIdCache;
    }

    @Override
    public Cache<BlockId, Long> getBlockHeightCache() {
        return _blockHeightCache;
    }

    @Override
    public UnspentTransactionOutputCache getUnspentTransactionOutputCache() {
        return _unspentTransactionOutputCache;
    }

    @Override
    public void commitLocalDatabaseManagerCache(final LocalDatabaseManagerCache localDatabaseManagerCache) { }

    @Override
    public void commit() { }

    @Override
    public UtxoCount getMaxCachedUtxoCount() { return UtxoCount.wrap(0L); }

    @Override
    public UnspentTransactionOutputCache newUnspentTransactionOutputCache() {
        return new DisabledUnspentTransactionOutputCache();
    }

    @Override
    public void close() { }
}
