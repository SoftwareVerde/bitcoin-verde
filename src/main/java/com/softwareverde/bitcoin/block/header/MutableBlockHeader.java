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

    protected static class ByteData {
        public final byte[] version = new byte[4];
        public final byte[] previousBlockHash = new byte[32];
        public final byte[] merkleRoot = new byte[32];
        public final byte[] timestamp = new byte[4];
        public final byte[] difficulty = new byte[4];
        public final byte[] nonce = new byte[4];

        public byte[] serialize() {
            final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
            byteArrayBuilder.appendBytes(this.version, Endian.LITTLE);
            byteArrayBuilder.appendBytes(this.previousBlockHash, Endian.LITTLE);
            byteArrayBuilder.appendBytes(this.merkleRoot, Endian.LITTLE);
            byteArrayBuilder.appendBytes(this.timestamp, Endian.LITTLE);
            byteArrayBuilder.appendBytes(this.difficulty, Endian.LITTLE);
            byteArrayBuilder.appendBytes(this.nonce, Endian.LITTLE);
            return byteArrayBuilder.build();
        }
    }

    protected ByteData _createByteData() {
        final ByteData byteData = new ByteData();
        ByteUtil.setBytes(byteData.version, ByteUtil.integerToBytes(_version));
        ByteUtil.setBytes(byteData.previousBlockHash, _previousBlockHash.getBytes());
        ByteUtil.setBytes(byteData.merkleRoot, _merkleRoot.getBytes());

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
        final ByteData byteData = _createByteData();
        final byte[] serializedByteData = byteData.serialize();
        return new ImmutableHash(ByteUtil.reverseBytes(BitcoinUtil.sha256(BitcoinUtil.sha256(serializedByteData))));
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
        final ByteData byteData = _createByteData();

        final byte[] transactionCount = new byte[1];
        ByteUtil.setBytes(transactionCount, ByteUtil.integerToBytes(_getTransactionCount()));

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(byteData.serialize(), Endian.BIG);
        byteArrayBuilder.appendBytes(transactionCount, Endian.LITTLE);
        return byteArrayBuilder.build();
    }

    @Override
    public Boolean validateBlockHeader() {
        final Hash sha256Hash = _calculateSha256Hash();
        return (_difficulty.isSatisfiedBy(sha256Hash));
    }
}
