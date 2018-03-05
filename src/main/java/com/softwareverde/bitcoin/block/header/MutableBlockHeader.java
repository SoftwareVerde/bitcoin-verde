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

    protected Hash _calculateSha256Hash() {
        final BlockHeaderDeflater blockHeaderDeflater = new BlockHeaderDeflater();
        final byte[] serializedByteData = blockHeaderDeflater.toBytes(this);
        return new ImmutableHash(ByteUtil.reverseEndian(BitcoinUtil.sha256(BitcoinUtil.sha256(serializedByteData))));
    }

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
    public Hash getHash() {
        return _calculateSha256Hash();
    }

    @Override
    public Boolean isValid() {
        final Hash sha256Hash = _calculateSha256Hash();
        return (_difficulty.isSatisfiedBy(sha256Hash));
    }

    @Override
    public ImmutableBlockHeader asConst() {
        return new ImmutableBlockHeader(this);
    }
}
