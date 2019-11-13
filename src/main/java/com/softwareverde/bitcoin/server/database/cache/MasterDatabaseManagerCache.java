package com.softwareverde.bitcoin.server.database.cache;

import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.hash.sha256.ImmutableSha256Hash;
import com.softwareverde.bitcoin.server.database.cache.utxo.UnspentTransactionOutputCache;
import com.softwareverde.bitcoin.server.database.cache.utxo.UtxoCount;
import com.softwareverde.bitcoin.transaction.ConstTransaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;

public interface MasterDatabaseManagerCache extends AutoCloseable {
    Cache<TransactionId, ConstTransaction> getTransactionCache();

    Cache<ImmutableSha256Hash, TransactionId> getTransactionIdCache();

    Cache<CachedTransactionOutputIdentifier, TransactionOutputId> getTransactionOutputIdCache();

    Cache<BlockId, BlockchainSegmentId> getBlockIdBlockchainSegmentIdCache();

    Cache<String, AddressId> getAddressIdCache();

    Cache<BlockId, Long> getBlockHeightCache();

    UnspentTransactionOutputCache getUnspentTransactionOutputCache();

    void commitLocalDatabaseManagerCache(LocalDatabaseManagerCache localDatabaseManagerCache);

    void commit();

    UtxoCount getMaxCachedUtxoCount();

    UnspentTransactionOutputCache newUnspentTransactionOutputCache();

    @Override
    void close();
}
