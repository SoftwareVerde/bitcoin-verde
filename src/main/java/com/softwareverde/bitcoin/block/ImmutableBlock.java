package com.softwareverde.bitcoin.block;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.ImmutableBlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.merkleroot.MerkleTree;
import com.softwareverde.bitcoin.block.merkleroot.MerkleTreeNode;
import com.softwareverde.bitcoin.transaction.ImmutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.merkleroot.MerkleRoot;
import com.softwareverde.constable.Const;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;

public class ImmutableBlock extends ImmutableBlockHeader implements Block, Const {
    private final Hash _hash;
    private final Hash _previousBlockHash;
    private final MerkleRoot _merkleRoot;
    private final Difficulty _difficulty;
    private final Long _timestamp;
    private final Integer _version;
    private final Long _nonce;
    private final List<ImmutableTransaction> _transactions;

    protected ImmutableBlock(final BlockHeader blockHeader, final List<? extends Transaction> transactions) {
        _previousBlockHash = blockHeader.getPreviousBlockHash();
        _merkleRoot = blockHeader.getMerkleRoot();
        _difficulty = blockHeader.getDifficulty();
        _timestamp = blockHeader.getTimestamp();
        _version = blockHeader.getVersion();
        _nonce = blockHeader.getNonce();

        final BlockHasher blockHasher = new BlockHasher();
        _hash = blockHasher.calculateBlockHash(blockHeader);

        final ImmutableListBuilder<ImmutableTransaction> immutableListBuilder = new ImmutableListBuilder<ImmutableTransaction>(transactions.getSize());
        for (final Transaction transaction : transactions) {
            immutableListBuilder.add(transaction.asConst());
        }
        _transactions = immutableListBuilder.build();
    }

    @Override
    public Integer getVersion() {
        return _version;
    }

    @Override
    public Hash getPreviousBlockHash() {
        return _previousBlockHash;
    }

    @Override
    public MerkleRoot getMerkleRoot() {
        return _merkleRoot;
    }

    @Override
    public Long getTimestamp() {
        return _timestamp;
    }

    @Override
    public Difficulty getDifficulty() {
        return _difficulty;
    }

    @Override
    public Long getNonce() {
        return _nonce;
    }

    @Override
    public Hash getHash() {
        return _hash;
    }

    @Override
    public Boolean isValid() {
        final BlockHasher blockHasher = new BlockHasher();
        final MerkleTree merkleTree = new MerkleTreeNode();

        for (final Transaction transaction : _transactions) {
            merkleTree.addItem(transaction);
        }

        final Hash blockHash = blockHasher.calculateBlockHash(this);
        final MerkleRoot merkleRoot = merkleTree.getMerkleRoot();

        return (blockHash.equals(_hash) && merkleRoot.equals(_merkleRoot));
    }

    @Override
    public List<ImmutableTransaction> getTransactions() {
        return _transactions;
    }

    @Override
    public ImmutableBlock asConst() {
        return this;
    }
}
