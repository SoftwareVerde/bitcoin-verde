package com.softwareverde.bitcoin.server.database.cache;

import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;

public interface MasterDatabaseManagerCache extends AutoCloseable {
    Cache<TransactionId, Transaction> getTransactionCache();
    Cache<Sha256Hash, TransactionId> getTransactionIdCache();
    Cache<CachedTransactionOutputIdentifier, TransactionOutputId> getTransactionOutputIdCache();
    Cache<BlockId, BlockchainSegmentId> getBlockIdBlockchainSegmentIdCache();
    Cache<String, AddressId> getAddressIdCache();
    Cache<BlockId, Long> getBlockHeightCache();

    void commitLocalDatabaseManagerCache(LocalDatabaseManagerCache localDatabaseManagerCache);
    void commit();

    @Override
    void close();
}
