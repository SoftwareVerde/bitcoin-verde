package com.softwareverde.bitcoin.server.database.cache.utxo;

import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.list.List;

public interface UnspentTransactionOutputCache extends AutoCloseable {

    void setMasterCache(UnspentTransactionOutputCache masterCache);

    void cacheUnspentTransactionOutputId(Sha256Hash transactionHash, Integer transactionOutputIndex, TransactionOutputId transactionOutputId);
    TransactionOutputId getCachedUnspentTransactionOutputId(Sha256Hash transactionHash, Integer transactionOutputIndex);
    void invalidateUnspentTransactionOutputId(TransactionOutputIdentifier transactionOutputIdentifier);
    void invalidateUnspentTransactionOutputIds(List<TransactionOutputIdentifier> transactionOutputIdentifiers);
    void commit(UnspentTransactionOutputCache sourceCache);
    void commit();

    @Override
    void close();
}
