package com.softwareverde.bitcoin.block;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.ImmutableBlockHeader;
import com.softwareverde.bitcoin.block.merkleroot.MerkleTree;
import com.softwareverde.bitcoin.block.merkleroot.MerkleTreeNode;
import com.softwareverde.bitcoin.transaction.ImmutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.type.merkleroot.MerkleRoot;
import com.softwareverde.constable.Const;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.util.ConstUtil;

public class ImmutableBlock extends ImmutableBlockHeader implements Block, Const {
    private final List<ImmutableTransaction> _transactions;

    protected ImmutableBlock(final BlockHeader blockHeader, final List<Transaction> transactions) {
        super(blockHeader);

        _transactions = ImmutableListBuilder.newConstListOfConstItems(transactions);
    }

    @Override
    public Boolean isValid() {
        final Boolean superIsValid = super.isValid();
        if (! superIsValid) { return false; }

        final MerkleTree merkleTree = new MerkleTreeNode();

        for (final Transaction transaction : _transactions) {
            merkleTree.addItem(transaction);
        }
        final MerkleRoot calculatedMerkleRoot = merkleTree.getMerkleRoot();

        return (calculatedMerkleRoot.equals(_merkleRoot));
    }

    @Override
    public List<Transaction> getTransactions() {
        return ConstUtil.downcastList(_transactions);
    }

    @Override
    public ImmutableBlock asConst() {
        return this;
    }
}
