package com.softwareverde.bitcoin.block.header;

import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.hash.ImmutableHash;
import com.softwareverde.bitcoin.type.hash.MutableHash;
import com.softwareverde.bitcoin.type.merkleroot.MerkleRoot;
import com.softwareverde.bitcoin.type.merkleroot.MutableMerkleRoot;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class MutableBlockHeader implements BlockHeader {
    protected Integer _version;
    protected Hash _previousBlockHash = new MutableHash();
    protected MerkleRoot _merkleRoot = new MutableMerkleRoot();
    protected Long _timestamp;
    protected Difficulty _difficulty;
    protected Long _nonce;

    protected BlockHeaderByteData _createByteData() {
        final BlockHeaderByteData blockHeaderByteData = new BlockHeaderByteData();
        ByteUtil.setBytes(blockHeaderByteData.version, ByteUtil.integerToBytes(_version));
        ByteUtil.setBytes(blockHeaderByteData.previousBlockHash, _previousBlockHash.getBytes());
        ByteUtil.setBytes(blockHeaderByteData.merkleRoot, _merkleRoot.getBytes());

        final byte[] timestampBytes = ByteUtil.longToBytes(_timestamp);
        for (int i = 0; i< blockHeaderByteData.timestamp.length; ++i) {
            blockHeaderByteData.timestamp[(blockHeaderByteData.timestamp.length - i) - 1] = timestampBytes[(timestampBytes.length - i) - 1];
        }

        ByteUtil.setBytes(blockHeaderByteData.difficulty, _difficulty.encode());

        final byte[] nonceBytes = ByteUtil.longToBytes(_nonce);
        for (int i = 0; i< blockHeaderByteData.nonce.length; ++i) {
            blockHeaderByteData.nonce[(blockHeaderByteData.nonce.length - i) - 1] = nonceBytes[(nonceBytes.length - i) - 1];
        }

        return blockHeaderByteData;
    }

    protected Hash _calculateSha256Hash() {
        final BlockHeaderByteData blockHeaderByteData = _createByteData();
        final byte[] serializedByteData = blockHeaderByteData.serialize();
        return new ImmutableHash(ByteUtil.reverseEndian(BitcoinUtil.sha256(BitcoinUtil.sha256(serializedByteData))));
    }

    protected Integer _getTransactionCount() { return 0; }

    public MutableBlockHeader() {
        _version = VERSION;
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
    public MerkleRoot getMerkleRoot() { return _merkleRoot; }
    public void setMerkleRoot(final MerkleRoot merkleRoot) {
        _merkleRoot = merkleRoot;
    }

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
    public byte[] getBytes() {
        final BlockHeaderByteData blockHeaderByteData = _createByteData();

        final byte[] transactionCount = new byte[1];
        ByteUtil.setBytes(transactionCount, ByteUtil.integerToBytes(_getTransactionCount()));

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(blockHeaderByteData.serialize(), Endian.BIG);
        byteArrayBuilder.appendBytes(transactionCount, Endian.LITTLE);
        return byteArrayBuilder.build();
    }

    @Override
    public Boolean validateBlockHeader() {
        final Hash sha256Hash = _calculateSha256Hash();
        return (_difficulty.isSatisfiedBy(sha256Hash));
    }
}
