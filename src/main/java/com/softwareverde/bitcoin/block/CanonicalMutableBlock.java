package com.softwareverde.bitcoin.block;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.constable.list.List;

import java.util.Comparator;

public class CanonicalMutableBlock extends MutableBlock {
    public static final Comparator<Transaction> LEXICAL_TRANSACTION_ORDERING = new Comparator<Transaction>() {
        @Override
        public int compare(final Transaction transaction0, final Transaction transaction1) {
            final Sha256Hash hash0 = transaction0.getHash();
            final Sha256Hash hash1 = transaction1.getHash();

            return Sha256Hash.COMPARATOR.compare(hash0, hash1);
        }
    };

    // Adds newTransaction to the Block's set of transactions in lexical order.
    //  Rebuilds the Block's MerkleTree.
    //  If newTransaction is a duplicate then it is not added to the Block, but the MerkleTree is still rebuilt.
    protected void _addTransaction(final Transaction newTransaction) {
        final Transaction newConstTransaction = newTransaction.asConst();

        _merkleTree.clear();

        if (! _transactions.isEmpty()) {
            final Transaction coinbaseTransaction = _transactions.get(0);
            _merkleTree.addItem(coinbaseTransaction);
        }

        boolean wasAdded = false;
        for (int i = 1; i < _transactions.getCount(); ++i) { // Exclude coinbase...
            final Transaction existingTransaction = _transactions.get(i);

            if (! wasAdded) {
                final int compareValue = LEXICAL_TRANSACTION_ORDERING.compare(existingTransaction, newConstTransaction);
                if (compareValue == 0) { wasAdded = true; } // Transaction is a duplicate; wasAdded is set to true to prevent newTransaction from being added again...
                else {
                    final boolean newTransactionComesBeforeExistingTransaction = (compareValue > 0);
                    if (newTransactionComesBeforeExistingTransaction) {
                        _transactions.add(i, newConstTransaction); // Insert newTransaction before existingTransaction...
                        _merkleTree.addItem(newConstTransaction);

                        wasAdded = true;
                        continue; // Continue rebuilding the MerkleTree...
                    }
                }
            }

            _merkleTree.addItem(existingTransaction);
        }

        if (! wasAdded) { // Add newTransaction at the end of the Block or as its Coinbase...
            _transactions.add(newConstTransaction);
            _merkleTree.addItem(newConstTransaction);
        }
    }

    @Override
    protected void _initTransactions(final List<Transaction> transactions) {
        _transactions.clear();
        if (transactions.isEmpty()) { return; }

        for (int i = 1; i < transactions.getCount(); ++i) { // Excludes coinbase...
            final Transaction transaction = transactions.get(i);
            final Transaction constTransaction = transaction.asConst();
            _transactions.add(constTransaction);
        }

        _transactions.sort(LEXICAL_TRANSACTION_ORDERING);
        _transactions.add(0, transactions.get(0)); // Adds the coinbase to the front...
    }

    public CanonicalMutableBlock() { }

    public CanonicalMutableBlock(final BlockHeader blockHeader) {
        super(blockHeader);
    }

    public CanonicalMutableBlock(final BlockHeader blockHeader, final List<Transaction> transactions) {
        super(blockHeader, transactions);
    }

    public CanonicalMutableBlock(final Block block) {
        super(block);
    }

    @Override
    public void addTransaction(final Transaction newTransaction) {
        _addTransaction(newTransaction);
    }

    @Override
    public void replaceTransaction(final Integer index, final Transaction transaction) {
        if (index == 0) { // Traditionally replace the coinbase...
            super.replaceTransaction(0, transaction);
            return;
        }

        _transactions.remove(index);
        _addTransaction(transaction); // Also rebuilds the MerkleTree...
    }
}
