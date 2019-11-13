package com.softwareverde.bitcoin.block;

import com.softwareverde.bitcoin.block.header.BlockHeaderWithTransactionCount;
import com.softwareverde.bitcoin.block.merkleroot.PartialMerkleTree;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.coinbase.CoinbaseTransaction;
import com.softwareverde.bloomfilter.BloomFilter;
import com.softwareverde.constable.list.List;

public interface Block extends BlockHeaderWithTransactionCount {
    List<Transaction> getTransactions();
    List<Transaction> getTransactions(BloomFilter bloomFilter);
    CoinbaseTransaction getCoinbaseTransaction();
    List<Sha256Hash> getPartialMerkleTree(Integer transactionIndex);
    PartialMerkleTree getPartialMerkleTree(BloomFilter bloomFilter);
    Boolean hasTransaction(Transaction transaction);

    @Override
    ImmutableBlock asConst();
}
