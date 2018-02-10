package com.softwareverde.bitcoin.block.header;

import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class BlockHeader {
    public static final Integer VERSION = 0x04;

    protected Integer _version;
    protected final byte[] _previousBlockHash = new byte[32];
    protected final byte[] _merkleRoot = new byte[32];
    protected Long _timestamp;
    protected Integer _difficulty;
    protected Long _nonce;
    protected Integer _transactionCount;

    public BlockHeader() {
        _version = VERSION;
    }

    public byte[] getBytes() {
        final byte[] version = new byte[4];
        final byte[] previousBlockHash = new byte[32];
        final byte[] merkleRoot = new byte[32];
        final byte[] timestamp = new byte[4];
        final byte[] difficulty = new byte[4];
        final byte[] nonce = new byte[4];
        final byte[] transactionCount = new byte[1];

        ByteUtil.setBytes(version, ByteUtil.integerToBytes(_version));
        ByteUtil.setBytes(previousBlockHash, _previousBlockHash);
        ByteUtil.setBytes(merkleRoot, _merkleRoot);
        ByteUtil.setBytes(timestamp, ByteUtil.longToBytes(_timestamp));
        ByteUtil.setBytes(difficulty, ByteUtil.integerToBytes(_difficulty));
        ByteUtil.setBytes(nonce, ByteUtil.longToBytes(_nonce));
        ByteUtil.setBytes(transactionCount, ByteUtil.integerToBytes(_transactionCount));

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(version, Endian.LITTLE);
        byteArrayBuilder.appendBytes(previousBlockHash, Endian.LITTLE);
        byteArrayBuilder.appendBytes(merkleRoot, Endian.LITTLE);
        byteArrayBuilder.appendBytes(timestamp, Endian.LITTLE);
        byteArrayBuilder.appendBytes(difficulty, Endian.LITTLE);
        byteArrayBuilder.appendBytes(nonce, Endian.LITTLE);
        byteArrayBuilder.appendBytes(transactionCount, Endian.LITTLE);
        return byteArrayBuilder.build();
    }
}
