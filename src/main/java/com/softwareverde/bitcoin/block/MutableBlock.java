package com.softwareverde.bitcoin.block;

import com.softwareverde.bitcoin.block.header.AbstractBlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.merkleroot.MerkleTree;
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

public class MutableBlock extends AbstractBlockHeader implements Block {
    protected final BlockDeflater _blockDeflater;
    protected final MerkleTreeNode<Transaction> _merkleTree = new MerkleTreeNode<Transaction>();
    protected final MutableList<Transaction> _transactions = new MutableList<Transaction>();

    protected Integer _cachedHashCode = null;
    protected Sha256Hash _cachedHash = null;

    protected MutableBlock(final BlockHasher blockHasher, final BlockDeflater blockDeflater) {
        super(blockHasher);
        _blockDeflater = blockDeflater;
    }

    public MutableBlock() {
        _blockDeflater = new BlockDeflater();
        _merkleRoot = null;
    }

    public MutableBlock(final BlockHeader blockHeader) {
        super(blockHeader);
        _blockDeflater = new BlockDeflater();
        _merkleRoot = null;
    }

    public MutableBlock(final Block block) {
        this(block, block.getTransactions());
    }

    public MutableBlock(final BlockHeader blockHeader, final List<Transaction> transactions) {
        super(blockHeader);
        _blockDeflater = new BlockDeflater();
        _merkleRoot = null;

        if (transactions != null) {
            for (final Transaction transaction : transactions) {
                final Transaction constTransaction = transaction.asConst();
                _transactions.add(constTransaction);
                _merkleTree.addItem(constTransaction);
            }
        }
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
        _cachedHash = null;
        _merkleRoot = null;
    }

    public void replaceTransaction(final Integer index, final Transaction transaction) {
        final Transaction constTransaction = transaction.asConst();
        _transactions.set(index, constTransaction);
        _merkleTree.replaceItem(index, constTransaction);

        _cachedHashCode = null;
        _cachedHash = null;
        _merkleRoot = null;
    }

    public void removeTransaction(final Sha256Hash transactionHashToRemove) {
        _merkleTree.clear();

        int index = 0;
        final int transactionCount = _transactions.getCount();
        for (int i = 0; i < transactionCount; ++i) {
            final Transaction transaction = _transactions.get(index);
            final Sha256Hash transactionHash = transaction.getHash();
            if (Util.areEqual(transactionHashToRemove, transactionHash)) {
                _transactions.remove(index);
            }
            else {
                _merkleTree.addItem(transaction);
                index += 1;
            }
        }

        _cachedHashCode = null;
        _cachedHash = null;
        _merkleRoot = null;
    }

    public void clearTransactions() {
        _transactions.clear();
        _merkleTree.clear();

        _cachedHashCode = null;
        _cachedHash = null;
        _merkleRoot = null;
    }

    public void setVersion(final Long version) {
        _version = version;

        _cachedHashCode = null;
        _cachedHash = null;
    }

    public void setPreviousBlockHash(final Sha256Hash previousBlockHash) {
        _previousBlockHash = previousBlockHash.asConst();

        _cachedHashCode = null;
        _cachedHash = null;
    }

    @Override
    public MerkleRoot getMerkleRoot() {
        final MerkleRoot cachedMerkleRoot = _merkleRoot;
        if (cachedMerkleRoot != null) { return cachedMerkleRoot; }

        final MerkleRoot merkleRoot = _merkleTree.getMerkleRoot();
        _merkleRoot = merkleRoot;
        return merkleRoot;
    }

    public void setTimestamp(final Long timestamp) {
        _timestamp = timestamp;

        _cachedHashCode = null;
        _cachedHash = null;
    }

    public void setDifficulty(final Difficulty difficulty) {
        _difficulty = difficulty.asConst();

        _cachedHashCode = null;
        _cachedHash = null;
    }

    public void setNonce(final Long nonce) {
        _nonce = nonce;

        _cachedHashCode = null;
        _cachedHash = null;
    }

    @Override
    public Boolean isValid() {
        if (_transactions.isEmpty()) { return false; }

        return super.isValid();
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
    public MerkleTree<Transaction> getMerkleTree() {
        return _merkleTree;
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
        return _blockDeflater.toJson(this);
    }

    @Override
    public int hashCode() {
        final Integer cachedHashCode = _cachedHashCode;
        if (cachedHashCode != null) { return cachedHashCode; }

        final int hashCode = super.hashCode();
        _cachedHashCode = hashCode;
        return hashCode;
    }
}
