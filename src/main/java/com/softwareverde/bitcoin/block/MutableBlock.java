package com.softwareverde.bitcoin.block;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.merkleroot.MerkleTreeNode;
import com.softwareverde.bitcoin.block.merkleroot.PartialMerkleTree;
import com.softwareverde.bitcoin.merkleroot.MerkleRoot;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionBloomFilterMatcher;
import com.softwareverde.bitcoin.transaction.coinbase.CoinbaseTransaction;
import com.softwareverde.bloomfilter.BloomFilter;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.json.Json;
import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.util.Util;

public class MutableBlock implements Block {
    protected Long _version;
    protected Sha256Hash _previousBlockHash = Sha256Hash.EMPTY_HASH;
    protected Long _timestamp;
    protected Difficulty _difficulty;
    protected Long _nonce;
    protected MerkleTreeNode<Transaction> _merkleTree = new MerkleTreeNode<Transaction>();
    protected MutableList<Transaction> _transactions = new MutableList<Transaction>();

    protected Integer _cachedHashCode = null;

    protected void _initFromBlockHeader(final BlockHeader blockHeader) {
        final Sha256Hash previousBlockHash = blockHeader.getPreviousBlockHash();
        final Difficulty difficulty = blockHeader.getDifficulty();

        _version = blockHeader.getVersion();
        _previousBlockHash = previousBlockHash.asConst();
        _timestamp = blockHeader.getTimestamp();
        _difficulty = difficulty.asConst();
        _nonce = blockHeader.getNonce();
    }

    protected void _initTransactions(final List<Transaction> transactions) {
        for (final Transaction transaction : transactions) {
            final Transaction constTransaction = transaction.asConst();
            _transactions.add(constTransaction);
            _merkleTree.addItem(constTransaction);
        }
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

    @Override
    public Boolean hasTransaction(final Transaction transaction) {
        for (final Transaction existingTransaction : _transactions) {
            if (Util.areEqual(transaction, existingTransaction)) {
                return true;
            }
        }

        return false;
    }

    public void addTransaction(final Transaction transaction) {
        final Transaction constTransaction = transaction.asConst();
        _transactions.add(constTransaction);
        _merkleTree.addItem(constTransaction);
        _cachedHashCode = null;
    }

    public void replaceTransaction(final Integer index, final Transaction transaction) {
        final Transaction constTransaction = transaction.asConst();
        _transactions.set(index, constTransaction);
        _merkleTree.replaceItem(index, constTransaction);
        _cachedHashCode = null;
    }

    public void removeTransaction(final Sha256Hash transactionHashToRemove) {
        _merkleTree.clear();
        _cachedHashCode = null;

        final MutableList<Transaction> oldTransactions = _transactions;
        _transactions = new MutableList<Transaction>(_transactions.getCount());
        for (final Transaction transaction : oldTransactions) {
            final Sha256Hash transactionHash = transaction.getHash();
            if (! Util.areEqual(transactionHashToRemove, transactionHash)) {
                _transactions.add(transaction);
                _merkleTree.addItem(transaction);
            }
        }
    }

    public void clearTransactions() {
        _transactions.clear();
        _merkleTree.clear();
        _cachedHashCode = null;
    }

    @Override
    public Long getVersion() { return _version; }

    public void setVersion(final Long version) {
        _version = version;
        _cachedHashCode = null;
    }

    @Override
    public Sha256Hash getPreviousBlockHash() { return _previousBlockHash; }

    public void setPreviousBlockHash(final Sha256Hash previousBlockHash) {
        _previousBlockHash = previousBlockHash.asConst();
        _cachedHashCode = null;
    }

    @Override
    public MerkleRoot getMerkleRoot() { return _merkleTree.getMerkleRoot(); }

    @Override
    public Long getTimestamp() { return _timestamp; }

    public void setTimestamp(final Long timestamp) {
        _timestamp = timestamp;
        _cachedHashCode = null;
    }

    @Override
    public Difficulty getDifficulty() { return _difficulty; }

    public void setDifficulty(final Difficulty difficulty) {
        _difficulty = difficulty.asConst();
        _cachedHashCode = null;
    }

    @Override
    public Long getNonce() { return  _nonce; }

    public void setNonce(final Long nonce) {
        _nonce = nonce;
        _cachedHashCode = null;
    }

    @Override
    public Sha256Hash getHash() {
        final BlockHasher blockHasher = new BlockHasher();
        return blockHasher.calculateBlockHash(this);
    }

    @Override
    public Boolean isValid() {
        if (_transactions.isEmpty()) { return false; }

        final BlockHasher blockHasher = new BlockHasher();
        final Sha256Hash sha256Hash = blockHasher.calculateBlockHash(this);
        return (_difficulty.isSatisfiedBy(sha256Hash));
    }

    @Override
    public List<Transaction> getTransactions() {
        return _transactions;
    }

    @Override
    public List<Transaction> getTransactions(final BloomFilter bloomFilter) {
        final ImmutableListBuilder<Transaction> matchedTransactions = new ImmutableListBuilder<Transaction>();
        for (final Transaction transaction : _transactions) {
            if (transaction.matches(bloomFilter)) {
                matchedTransactions.add(transaction);
            }
        }
        return matchedTransactions.build();
    }

    @Override
    public CoinbaseTransaction getCoinbaseTransaction() {
        if (_transactions.isEmpty()) { return null; }

        final Transaction transaction = _transactions.get(0);
        return transaction.asCoinbase();
    }

    @Override
    public List<Sha256Hash> getPartialMerkleTree(final Integer transactionIndex) {
        if (_merkleTree.isEmpty()) { return new MutableList<Sha256Hash>(); }
        return _merkleTree.getPartialTree(transactionIndex);
    }

    @Override
    public PartialMerkleTree getPartialMerkleTree(final BloomFilter bloomFilter) {
        final TransactionBloomFilterMatcher transactionBloomFilterMatcher = new TransactionBloomFilterMatcher(bloomFilter);
        return _merkleTree.getPartialTree(transactionBloomFilterMatcher);
    }

    @Override
    public ImmutableBlock asConst() {
        return new ImmutableBlock(this, _transactions);
    }

    @Override
    public Integer getTransactionCount() {
        return _transactions.getCount();
    }

    @Override
    public Json toJson() {
        final BlockDeflater blockDeflater = new BlockDeflater();
        return blockDeflater.toJson(this);
    }

    @Override
    public int hashCode() {
        final Integer cachedHashCode = _cachedHashCode;
        if (cachedHashCode != null) { return cachedHashCode; }

        final BlockHasher blockHasher = new BlockHasher();
        final Integer hashCode = blockHasher.calculateBlockHash(this).hashCode();
        _cachedHashCode = hashCode;
        return hashCode;
    }
}
