package com.softwareverde.bitcoin.block.header;

import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayBuilder;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class BlockHeaderDeflater {
    protected static class BlockHeaderByteData {
        public final byte[] version = new byte[4];
        public final byte[] previousBlockHash = new byte[32];
        public final byte[] merkleRoot = new byte[32];
        public final byte[] timestamp = new byte[4];
        public final byte[] difficulty = new byte[4];
        public final byte[] nonce = new byte[4];
    }

    protected ByteArrayBuilder _serializeByteData(final BlockHeaderByteData byteData) {
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(byteData.version, Endian.LITTLE);
        byteArrayBuilder.appendBytes(byteData.previousBlockHash, Endian.LITTLE);
        byteArrayBuilder.appendBytes(byteData.merkleRoot, Endian.LITTLE);
        byteArrayBuilder.appendBytes(byteData.timestamp, Endian.LITTLE);
        byteArrayBuilder.appendBytes(byteData.difficulty, Endian.LITTLE);
        byteArrayBuilder.appendBytes(byteData.nonce, Endian.LITTLE);
        return byteArrayBuilder;
    }

    protected BlockHeaderByteData _createByteData(final BlockHeader blockHeader) {
        final BlockHeaderByteData byteData = new BlockHeaderByteData();
        ByteUtil.setBytes(byteData.version, ByteUtil.integerToBytes(blockHeader.getVersion()));
        ByteUtil.setBytes(byteData.previousBlockHash, blockHeader.getPreviousBlockHash().getBytes());
        ByteUtil.setBytes(byteData.merkleRoot, blockHeader.getMerkleRoot().getBytes());

        final byte[] timestampBytes = ByteUtil.longToBytes(blockHeader.getTimestamp());
        for (int i=0; i<byteData.timestamp.length; ++i) {
            byteData.timestamp[(byteData.timestamp.length - i) - 1] = timestampBytes[(timestampBytes.length - i) - 1];
        }

        ByteUtil.setBytes(byteData.difficulty, blockHeader.getDifficulty().encode());

        final byte[] nonceBytes = ByteUtil.longToBytes(blockHeader.getNonce());
        for (int i=0; i<byteData.nonce.length; ++i) {
            byteData.nonce[(byteData.nonce.length - i) - 1] = nonceBytes[(nonceBytes.length - i) - 1];
        }

        return byteData;
    }

    public byte[] toBytes(final BlockHeader blockHeader) {
        final BlockHeaderByteData blockHeaderByteData = _createByteData(blockHeader);
        final ByteArrayBuilder byteArrayBuilder = _serializeByteData(blockHeaderByteData);
        return byteArrayBuilder.build();
    }

    public ByteArrayBuilder toByteArrayBuilder(final BlockHeader blockHeader) {
        final BlockHeaderByteData blockHeaderByteData = _createByteData(blockHeader);
        final ByteArrayBuilder byteArrayBuilder = _serializeByteData(blockHeaderByteData);
        return byteArrayBuilder;
    }
}
