package com.softwareverde.bitcoin.block;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.merkleroot.MerkleTreeNode;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.coinbase.CoinbaseTransaction;
import com.softwareverde.bitcoin.transaction.coinbase.MutableCoinbaseTransaction;
import com.softwareverde.bitcoin.type.hash.sha256.ImmutableSha256Hash;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.type.merkleroot.MerkleRoot;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.json.Json;

public class MutableBlock implements Block {
    protected Long _version;
    protected Sha256Hash _previousBlockHash = new ImmutableSha256Hash();
    protected Long _timestamp;
    protected Difficulty _difficulty;
    protected Long _nonce;
    protected MerkleTreeNode<Transaction> _merkleTree = new MerkleTreeNode<Transaction>();
    protected MutableList<Transaction> _transactions = new MutableList<Transaction>();

    protected final BlockHasher _blockHasher = new BlockHasher();

    protected void _initFromBlockHeader(final BlockHeader blockHeader) {
        _version = blockHeader.getVersion();
        _previousBlockHash = blockHeader.getPreviousBlockHash().asConst();
        _timestamp = blockHeader.getTimestamp();
        _difficulty = blockHeader.getDifficulty().asConst();
        _nonce = blockHeader.getNonce();
    }

    protected void _initTransactions(final List<Transaction> transactions) {
        for (final Transaction transaction : transactions) {
            final Transaction constTransaction = transaction.asConst();
            _transactions.add(constTransaction);
            _merkleTree.addItem(constTransaction);
        }
    }

    @Override
    public Long getVersion() { return _version; }
    public void setVersion(final Long version) { _version = version; }

    @Override
    public Sha256Hash getPreviousBlockHash() { return _previousBlockHash; }
    public void setPreviousBlockHash(final Sha256Hash previousBlockHash) {
        _previousBlockHash = previousBlockHash.asConst();
    }

    @Override
    public MerkleRoot getMerkleRoot() { return _merkleTree.getMerkleRoot(); }

    @Override
    public Long getTimestamp() { return _timestamp; }
    public void setTimestamp(final Long timestamp) { _timestamp = timestamp; }

    @Override
    public Difficulty getDifficulty() { return _difficulty; }
    public void setDifficulty(final Difficulty difficulty) { _difficulty = difficulty; }

    @Override
    public Long getNonce() { return  _nonce; }
    public void setNonce(final Long nonce) { _nonce = nonce; }

    @Override
    public Sha256Hash getHash() {
        return _blockHasher.calculateBlockHash(this);
    }

    @Override
    public Boolean isValid() {
        if (_transactions.isEmpty()) { return false; }

        final Sha256Hash sha256Hash = _blockHasher.calculateBlockHash(this);
        return (_difficulty.isSatisfiedBy(sha256Hash));
    }

    public MutableBlock() {
        _version = VERSION;
    }

    public MutableBlock(final BlockHeader blockHeader) {
        _initFromBlockHeader(blockHeader);
    }

    public MutableBlock(final Block block) {
        _initFromBlockHeader(block);
        _initTransactions(block.getTransactions());
    }

    public MutableBlock(final BlockHeader blockHeader, final List<Transaction> transactions) {
        _initFromBlockHeader(blockHeader);
        _initTransactions(transactions);
    }

    public void addTransaction(final Transaction transaction) {
        final Transaction constTransaction = transaction.asConst();
        _transactions.add(constTransaction);
        _merkleTree.addItem(constTransaction);
    }

    public void replaceTransaction(final Integer index, final Transaction transaction) {
        final Transaction constTransaction = transaction.asConst();
        _transactions.set(index, constTransaction);
        _merkleTree.replaceItem(index, constTransaction);
    }

    public void clearTransactions() {
        _transactions.clear();
        _merkleTree.clear();
    }

    @Override
    public List<Transaction> getTransactions() {
        return _transactions;
    }

    @Override
    public CoinbaseTransaction getCoinbaseTransaction() {
        if (_transactions.isEmpty()) { return null; }

        final Transaction transaction = _transactions.get(0);
        return new MutableCoinbaseTransaction(transaction);
    }

    @Override
    public List<Sha256Hash> getPartialMerkleTree(final int transactionIndex) {
        if (_merkleTree.isEmpty()) { return new MutableList<Sha256Hash>(); }
        return _merkleTree.getPartialTree(transactionIndex);
    }

    @Override
    public ImmutableBlock asConst() {
        return new ImmutableBlock(this, _transactions);
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
