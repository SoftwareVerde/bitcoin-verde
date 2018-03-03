package com.softwareverde.bitcoin.block.header;

import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class BlockHeaderByteData {
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
