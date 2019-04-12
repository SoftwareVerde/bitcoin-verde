package com.softwareverde.bitcoin.block;

import com.softwareverde.bitcoin.block.header.BlockHeaderWithTransactionCount;
import com.softwareverde.bitcoin.block.merkleroot.MerkleTree;
import com.softwareverde.bitcoin.block.merkleroot.PartialMerkleTree;
import com.softwareverde.bitcoin.block.merkleroot.PartialMerkleTreeNode;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.coinbase.CoinbaseTransaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bloomfilter.BloomFilter;
import com.softwareverde.constable.list.List;

public interface Block extends BlockHeaderWithTransactionCount {
    static MerkleTree.Filter<Transaction> createMerkleTreeFilter(final BloomFilter bloomFilter) {
        return new MerkleTree.Filter<Transaction>() {
            @Override
            public boolean shouldInclude(final Transaction transaction) {
                if (transaction == null) { return false; }

                for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
                    final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
                    return bloomFilter.containsItem(transactionOutputIdentifier.toBytes());
                }
                return false;
            }
        };
    }

    List<Transaction> getTransactions();
    List<Transaction> getTransactions(BloomFilter bloomFilter);
    CoinbaseTransaction getCoinbaseTransaction();
    List<Sha256Hash> getPartialMerkleTree(Integer transactionIndex);
    PartialMerkleTree getPartialMerkleTree(BloomFilter bloomFilter);
    Boolean hasTransaction(Transaction transaction);

    @Override
    ImmutableBlock asConst();
}
