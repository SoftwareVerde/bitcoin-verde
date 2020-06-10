package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bloomfilter.BloomFilter;
import com.softwareverde.security.hash.sha256.Sha256Hash;

public interface MemoryPoolEnquirer {
    BloomFilter getBloomFilter(Sha256Hash blockHash);
    Integer getMemoryPoolTransactionCount();
    Transaction getTransaction(Sha256Hash transactionHash);
}
