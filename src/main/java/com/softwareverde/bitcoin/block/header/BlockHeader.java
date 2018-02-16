package com.softwareverde.bitcoin.block.header;

import com.softwareverde.bitcoin.block.header.difficulty.DifficultyEncoder;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class BlockHeader {
    public static final Integer VERSION = 0x04;
    public static final byte[] GENESIS_BLOCK_HEADER_HASH = BitcoinUtil.hexStringToByteArray("000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f");

    protected Integer _version;
    protected final byte[] _previousBlockHash = new byte[32];
    protected final byte[] _merkleRoot = new byte[32];
    protected Long _timestamp;
    protected Long _difficulty;
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
            byteArrayBuilder.appendBytes(this.difficulty, Endian.BIG);
            byteArrayBuilder.appendBytes(this.nonce, Endian.LITTLE);
            return byteArrayBuilder.build();
        }
    }

    protected ByteData _createByteData() {
        final DifficultyEncoder difficultyEncoder = new DifficultyEncoder();

        final ByteData byteData = new ByteData();
        ByteUtil.setBytes(byteData.version, ByteUtil.integerToBytes(_version));
        ByteUtil.setBytes(byteData.previousBlockHash, _previousBlockHash);
        ByteUtil.setBytes(byteData.merkleRoot, _merkleRoot);
        ByteUtil.setBytes(byteData.timestamp, ByteUtil.longToBytes(_timestamp));
        ByteUtil.setBytes(byteData.difficulty, difficultyEncoder.encodeDifficulty(_difficulty));
        ByteUtil.setBytes(byteData.nonce, ByteUtil.longToBytes(_nonce));
        return byteData;
    }

    protected Integer _getTransactionCount() { return 0; }

    public BlockHeader() {
        _version = VERSION;
    }

    public void setVersion(final Integer version) { _version = version; }
    public Integer getVersion() { return _version; }

    public void setPreviousBlockHash(final byte[] previousBlockHash) { ByteUtil.setBytes(_previousBlockHash, previousBlockHash); }
    public byte[] getPreviousBlockHash() { return ByteUtil.copyBytes(_previousBlockHash); }

    public void setMerkleRoot(final byte[] merkleRoot) { ByteUtil.setBytes(_merkleRoot, merkleRoot); }
    public byte[] getMerkleRoot() { return ByteUtil.copyBytes(_merkleRoot); }

    public void setTimestamp(final Long timestamp) { _timestamp = timestamp; }
    public Long getTimestamp() { return _timestamp; }

    public void setDifficulty(final Long difficulty) { _difficulty = difficulty; }
    public Long getDifficulty() { return _difficulty; }

    public void setNonce(final Long nonce) { _nonce = nonce; }
    public Long getNonce() { return  _nonce; }

    public byte[] getHash() {
        final ByteData byteData = _createByteData();
        final byte[] serializedByteData = byteData.serialize();
        return BitcoinUtil.sha256(BitcoinUtil.sha256(serializedByteData));
    }

    public byte[] getBytes() {
        final ByteData byteData = _createByteData();

        final byte[] transactionCount = new byte[1];
        ByteUtil.setBytes(transactionCount, ByteUtil.integerToBytes(_getTransactionCount()));

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(byteData.serialize(), Endian.BIG);
        byteArrayBuilder.appendBytes(transactionCount, Endian.LITTLE);
        return byteArrayBuilder.build();
    }
}
