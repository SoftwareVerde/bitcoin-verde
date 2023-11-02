package com.softwareverde.bitcoin.server.module.node.handler;

import com.softwareverde.bitcoin.server.module.node.MemoryPoolEnquirer;
import com.softwareverde.bitcoin.server.module.node.TransactionMempool;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionWithFee;
import com.softwareverde.bloomfilter.BloomFilter;
import com.softwareverde.bloomfilter.MutableBloomFilter;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public class MemoryPoolEnquirerHandler implements MemoryPoolEnquirer {
    protected final TransactionMempool _transactionMempool;

    public MemoryPoolEnquirerHandler(final TransactionMempool transactionMempool) {
        _transactionMempool = transactionMempool;
    }

    @Override
    public BloomFilter getBloomFilter(final Sha256Hash blockHash) {
        final List<TransactionWithFee> transactions = _transactionMempool.getTransactions();

        final MutableBloomFilter bloomFilter = MutableBloomFilter.newInstance((long) transactions.getCount(), 0.01D);

        for (final TransactionWithFee transaction : transactions) {
            final Sha256Hash transactionHash = transaction.transaction.getHash();
            bloomFilter.addItem(transactionHash);
        }

        return bloomFilter;
    }

    @Override
    public Integer getMemoryPoolTransactionCount() {
        return _transactionMempool.getCount();
    }

    @Override
    public Transaction getTransaction(final Sha256Hash transactionHash) {
        final TransactionWithFee transactionWithFee = _transactionMempool.getTransaction(transactionHash);
        if (transactionWithFee == null) { return null; }
        return transactionWithFee.transaction;
    }
}
