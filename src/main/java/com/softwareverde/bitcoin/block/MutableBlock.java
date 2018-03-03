package com.softwareverde.bitcoin.block;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderByteData;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.merkleroot.MerkleTreeNode;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.hash.ImmutableHash;
import com.softwareverde.bitcoin.type.hash.MutableHash;
import com.softwareverde.bitcoin.type.merkleroot.MerkleRoot;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;

import java.util.ArrayList;
import java.util.List;

public class MutableBlock implements Block {
    protected Integer _version;
    protected Hash _previousBlockHash = new MutableHash();
    protected Long _timestamp;
    protected Difficulty _difficulty;
    protected Long _nonce;
    protected MerkleTreeNode _merkleTree = new MerkleTreeNode();
    protected List<Transaction> _transactions = new ArrayList<Transaction>();

    protected BlockHeaderByteData _createByteData() {
        final BlockHeaderByteData byteData = new BlockHeaderByteData();
        ByteUtil.setBytes(byteData.version, ByteUtil.integerToBytes(_version));
        ByteUtil.setBytes(byteData.previousBlockHash, _previousBlockHash.getBytes());
        ByteUtil.setBytes(byteData.merkleRoot, _merkleTree.getMerkleRoot().getBytes());

        final byte[] timestampBytes = ByteUtil.longToBytes(_timestamp);
        for (int i=0; i<byteData.timestamp.length; ++i) {
            byteData.timestamp[(byteData.timestamp.length - i) - 1] = timestampBytes[(timestampBytes.length - i) - 1];
        }

        ByteUtil.setBytes(byteData.difficulty, _difficulty.encode());

        final byte[] nonceBytes = ByteUtil.longToBytes(_nonce);
        for (int i=0; i<byteData.nonce.length; ++i) {
            byteData.nonce[(byteData.nonce.length - i) - 1] = nonceBytes[(nonceBytes.length - i) - 1];
        }

        return byteData;
    }

    protected Hash _calculateSha256Hash() {
        final BlockHeaderByteData byteData = _createByteData();
        final byte[] serializedByteData = byteData.serialize();
        return new ImmutableHash(ByteUtil.reverseEndian(BitcoinUtil.sha256(BitcoinUtil.sha256(serializedByteData))));
    }

    @Override
    public Integer getVersion() { return _version; }
    public void setVersion(final Integer version) { _version = version; }

    @Override
    public Hash getPreviousBlockHash() { return _previousBlockHash; }
    public void setPreviousBlockHash(final Hash previousBlockHash) {
        _previousBlockHash = previousBlockHash;
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
    public Hash calculateSha256Hash() {
        return _calculateSha256Hash();
    }

    @Override
    public Boolean validateBlockHeader() {
        final Hash sha256Hash = _calculateSha256Hash();
        return (_difficulty.isSatisfiedBy(sha256Hash));
    }

    public MutableBlock() {
        _version = VERSION;
    }

    public MutableBlock(final BlockHeader blockHeader) {
        _version = blockHeader.getVersion();
        _previousBlockHash = blockHeader.getPreviousBlockHash();
        _timestamp = blockHeader.getTimestamp();
        _difficulty = blockHeader.getDifficulty();
        _nonce = blockHeader.getNonce();
    }

    public void addTransaction(final Transaction transaction) {
        _transactions.add(transaction);
        _merkleTree.addItem(transaction);
    }

    public void clearTransactions() {
        _transactions.clear();
        _merkleTree.clear();
    }

    public List<Transaction> getTransactions() {
        return _transactions;
    }

    @Override
    public MerkleRoot calculateMerkleRoot() {
        return _merkleTree.getMerkleRoot();
    }

    @Override
    public byte[] getBytes() {
        final BlockHeaderByteData byteData = _createByteData();
        final byte[] headerBytes = byteData.serialize();
        final int transactionCount = _transactions.size();

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(headerBytes, Endian.BIG);
        byteArrayBuilder.appendBytes(ByteUtil.variableLengthIntegerToBytes(transactionCount), Endian.LITTLE);

        for (int i=0; i<transactionCount; ++i) {
            final Transaction transaction = _transactions.get(i);
            final byte[] transactionBytes = transaction.getBytes();
            byteArrayBuilder.appendBytes(transactionBytes, Endian.BIG);
        }

        return byteArrayBuilder.build();
    }
}
