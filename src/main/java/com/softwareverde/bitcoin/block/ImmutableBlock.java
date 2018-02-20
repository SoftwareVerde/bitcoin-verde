package com.softwareverde.bitcoin.block;

import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.merkleroot.MerkleRoot;
import com.softwareverde.util.Util;

import java.util.List;

public class ImmutableBlock implements Block {
    protected final Block _block;

    public ImmutableBlock() {
        _block = new MutableBlock();
    }

    public ImmutableBlock(final Block block) {
        if (block instanceof ImmutableBlock) {
            _block = block;
            return;
        }

        final MutableBlock mutableBlock = new MutableBlock(block);
        for (final Transaction transaction : block.getTransactions()) {
            mutableBlock.addTransaction(transaction.copy()); // TODO refactor as: mutableBlock.addTransaction(new ImmutableTransaction(transaction));
        }
        _block = mutableBlock;
    }

    @Override
    public List<Transaction> getTransactions() {
        return Util.copyList(_block.getTransactions());
    }

    @Override
    public Integer getVersion() {
        return _block.getVersion();
    }

    @Override
    public Hash getPreviousBlockHash() {
        return _block.getPreviousBlockHash();
    }

    @Override
    public MerkleRoot getMerkleRoot() {
        return _block.getMerkleRoot();
    }

    @Override
    public Long getTimestamp() {
        return _block.getTimestamp();
    }

    @Override
    public Difficulty getDifficulty() {
        return _block.getDifficulty();
    }

    @Override
    public Long getNonce() {
        return _block.getNonce();
    }

    @Override
    public Hash calculateSha256Hash() {
        return _block.calculateSha256Hash();
    }

    @Override
    public byte[] getBytes() {
        return _block.getBytes();
    }

    @Override
    public Boolean validateBlockHeader() {
        return _block.validateBlockHeader();
    }
}
