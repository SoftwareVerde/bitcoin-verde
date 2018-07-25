package com.softwareverde.bitcoin.block.header;

import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.json.Json;
import com.softwareverde.util.DateUtil;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class BlockHeaderDeflater {
    public static class BlockHeaderByteData {
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
        return _serializeByteData(blockHeaderByteData);
    }

    public BlockHeaderByteData toByteData( final BlockHeader blockHeader) {
        return _createByteData(blockHeader);
    }

    public Json toJson(final BlockHeader blockHeader) {
        final Json json = new Json();

        json.put("hash", blockHeader.getHash());
        json.put("previousBlockHash", blockHeader.getPreviousBlockHash());
        json.put("merkleRoot", blockHeader.getMerkleRoot());
        json.put("version", blockHeader.getVersion());
        json.put("timestamp", DateUtil.Utc.timestampToDatetimeString(blockHeader.getTimestamp()));
        json.put("difficulty", HexUtil.toHexString(blockHeader.getDifficulty().encode()));
        json.put("nonce", blockHeader.getNonce());

        return json;
    }
}
