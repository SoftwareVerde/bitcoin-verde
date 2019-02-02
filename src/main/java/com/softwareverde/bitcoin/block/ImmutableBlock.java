package com.softwareverde.bitcoin.block;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.ImmutableBlockHeader;
import com.softwareverde.bitcoin.block.merkleroot.MerkleTree;
import com.softwareverde.bitcoin.block.merkleroot.MerkleTreeNode;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.merkleroot.MerkleRoot;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.coinbase.CoinbaseTransaction;
import com.softwareverde.bitcoin.transaction.coinbase.ImmutableCoinbaseTransaction;
import com.softwareverde.constable.Const;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.json.Json;
import com.softwareverde.util.Util;

public class ImmutableBlock extends ImmutableBlockHeader implements Block, Const {
    protected final List<Transaction> _transactions;
    protected MerkleTree<Transaction> _merkleTree = null;

    protected void _buildMerkleTree() {
        _merkleTree = new MerkleTreeNode<Transaction>();
        for (final Transaction transaction : _transactions) {
            _merkleTree.addItem(transaction);
        }
    }

    public ImmutableBlock(final BlockHeader blockHeader, final List<Transaction> transactions) {
        super(blockHeader);

        final ImmutableListBuilder<Transaction> immutableListBuilder = new ImmutableListBuilder<Transaction>(transactions.getSize());
        for (final Transaction transaction : transactions) {
            immutableListBuilder.add(transaction.asConst());
        }
        _transactions = immutableListBuilder.build();
    }

    @Override
    public Boolean isValid() {
        final Boolean superIsValid = super.isValid();
        if (! superIsValid) { return false; }

        if (_transactions.isEmpty()) { return false; }

        if (_merkleTree == null) {
            _buildMerkleTree();
        }
        final MerkleRoot calculatedMerkleRoot = _merkleTree.getMerkleRoot();
        return (calculatedMerkleRoot.equals(_merkleRoot));
    }

    @Override
    public List<Transaction> getTransactions() {
        return _transactions;
    }

    @Override
    public CoinbaseTransaction getCoinbaseTransaction() {
        if (_transactions.isEmpty()) { return null; }

        final Transaction transaction = _transactions.get(0);
        return new ImmutableCoinbaseTransaction(transaction);
    }

    @Override
    public Boolean hasTransaction(final Transaction transaction) {
        for (final Transaction existingTransaction : _transactions) {
            if (Util.areEqual(transaction, existingTransaction)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public List<Sha256Hash> getPartialMerkleTree(final int transactionIndex) {
        if (_merkleTree == null) {
            _buildMerkleTree();
        }
        return _merkleTree.getPartialTree(transactionIndex);
    }

    @Override
    public ImmutableBlock asConst() {
        return this;
    }

    @Override
    public Integer getTransactionCount() {
        return _transactions.getSize();
    }

    @Override
    public Json toJson() {
        final BlockDeflater blockDeflater = new BlockDeflater();
        return blockDeflater.toJson(this);
    }
}
