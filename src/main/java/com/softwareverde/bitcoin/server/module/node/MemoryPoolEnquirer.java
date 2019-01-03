package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bloomfilter.BloomFilter;

public interface MemoryPoolEnquirer {
    BloomFilter getBloomFilter(Sha256Hash blockHash);
    Integer getMemoryPoolTransactionCount();
    Transaction getTransaction(Sha256Hash transactionHash);
}
