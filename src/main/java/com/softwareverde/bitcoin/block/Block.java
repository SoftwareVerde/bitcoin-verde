package com.softwareverde.bitcoin.block;

import com.softwareverde.bitcoin.block.header.BlockHeaderWithTransactionCount;
import com.softwareverde.bitcoin.block.merkleroot.MerkleTree;
import com.softwareverde.bitcoin.block.merkleroot.PartialMerkleTree;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.coinbase.CoinbaseTransaction;
import com.softwareverde.bloomfilter.BloomFilter;
import com.softwareverde.constable.list.List;
import com.softwareverde.security.hash.sha256.Sha256Hash;

public interface Block extends BlockHeaderWithTransactionCount {
    List<Transaction> getTransactions();
    List<Transaction> getTransactions(BloomFilter bloomFilter);
    CoinbaseTransaction getCoinbaseTransaction();

    MerkleTree<Transaction> getMerkleTree();
    List<Sha256Hash> getPartialMerkleTree(Integer transactionIndex);
    PartialMerkleTree getPartialMerkleTree(BloomFilter bloomFilter);
    Boolean hasTransaction(Transaction transaction);

    @Override
    ImmutableBlock asConst();
}
