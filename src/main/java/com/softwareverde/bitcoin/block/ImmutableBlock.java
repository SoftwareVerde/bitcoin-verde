package com.softwareverde.bitcoin.block;

import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.ImmutableBlockHeader;
import com.softwareverde.bitcoin.block.merkleroot.MerkleTree;
import com.softwareverde.bitcoin.block.merkleroot.MerkleTreeNode;
import com.softwareverde.bitcoin.block.merkleroot.MutableMerkleTree;
import com.softwareverde.bitcoin.block.merkleroot.PartialMerkleTree;
import com.softwareverde.bitcoin.merkleroot.MerkleRoot;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionBloomFilterMatcher;
import com.softwareverde.bitcoin.transaction.coinbase.CoinbaseTransaction;
import com.softwareverde.bloomfilter.BloomFilter;
import com.softwareverde.constable.Const;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.json.Json;
import com.softwareverde.util.Util;

public class ImmutableBlock extends ImmutableBlockHeader implements Block, Const {
    protected static final BlockDeflater DEFAULT_BLOCK_DEFLATER = new BlockDeflater();
    protected static final AddressInflater DEFAULT_ADDRESS_INFLATER = new AddressInflater();

    protected final BlockDeflater _blockDeflater;
    protected final AddressInflater _addressInflater;
    protected final List<Transaction> _transactions;
    protected MerkleTree<Transaction> _merkleTree = null;

    protected Integer _cachedHashCode = null;
    protected Sha256Hash _cachedHash = null;
    protected Integer _cachedByteCount = null;

    protected Integer _calculateByteCount() {
        return _blockDeflater.getByteCount(this);
    }

    protected void _buildMerkleTree() {
        final MutableMerkleTree<Transaction> merkleTree = new MerkleTreeNode<Transaction>();
        for (final Transaction transaction : _transactions) {
            merkleTree.addItem(transaction);
        }
        _merkleTree = merkleTree;
    }

    protected void cacheByteCount(final Integer byteCount) {
        _cachedByteCount = byteCount;
    }

    protected ImmutableBlock(final BlockHeader blockHeader, final List<Transaction> transactions, final BlockDeflater blockDeflater, final AddressInflater addressInflater) {
        super(blockHeader);

        final ImmutableListBuilder<Transaction> immutableListBuilder = new ImmutableListBuilder<Transaction>(transactions.getCount());
        for (final Transaction transaction : transactions) {
            immutableListBuilder.add(transaction.asConst());
        }
        _transactions = immutableListBuilder.build();
        _blockDeflater = blockDeflater;
        _addressInflater = addressInflater;
    }

    public ImmutableBlock(final BlockHeader blockHeader, final List<Transaction> transactions) {
        this(blockHeader, transactions, DEFAULT_BLOCK_DEFLATER, DEFAULT_ADDRESS_INFLATER);
    }

    public ImmutableBlock(final Block block) {
        this(block, block.getTransactions());
    }

    @Override
    public Sha256Hash getHash() {
        final Sha256Hash cachedHash = _cachedHash;
        if (cachedHash != null) { return cachedHash; }

        final Sha256Hash hash = super.getHash();
        _cachedHash = hash;
        return hash;
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
        if (_merkleTree == null) {
            _buildMerkleTree();
        }
        return _merkleTree;
    }

    @Override
    public Boolean hasTransaction(final Sha256Hash transactionHash) {
        for (final Transaction existingTransaction : _transactions) {
            final Sha256Hash existingTransactionHash = existingTransaction.getHash();
            if (Util.areEqual(transactionHash, existingTransactionHash)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public Integer getByteCount() {
        final Integer cachedByteCount = _cachedByteCount;
        if (cachedByteCount != null) { return cachedByteCount; }

        final Integer byteCount = _calculateByteCount();
        _cachedByteCount = byteCount;
        return byteCount;
    }

    @Override
    public List<Sha256Hash> getPartialMerkleTree(final Integer transactionIndex) {
        if (_merkleTree == null) {
            _buildMerkleTree();
        }
        return _merkleTree.getPartialTree(transactionIndex);
    }

    @Override
    public PartialMerkleTree getPartialMerkleTree(final BloomFilter bloomFilter) {
        final TransactionBloomFilterMatcher transactionBloomFilterMatcher = new TransactionBloomFilterMatcher(bloomFilter, _addressInflater);
        return _merkleTree.getPartialTree(transactionBloomFilterMatcher);
    }

    @Override
    public ImmutableBlock asConst() {
        return this;
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

    @Override
    public boolean equals(final Object object) {
        return super.equals(object);
    }
}
